package br.ufs.dcomp.ChatRabbitMQ;
import java.util.*;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.io.FileOutputStream;
import java.io.File;
import java.lang.Thread;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiConsumer;

public class Chat {
  private String user;
  private String sendUser;
  private String sendGroup;
  private String userPrompt;
  private Connection connection;
  private Scanner scanner;
  private String uploadsDirectory;
  private Map<String, BiConsumer<String[], ChannelWrapper>> commands;

  public Chat(String host, String username, String password, Scanner scanner) throws Exception{
    this.uploadsDirectory = "downloads";
    this.commands = new HashMap<>();
    this.commands.put("addGroup", this::createGroup);
    this.commands.put("addUser", this::addUserGroup);
    this.commands.put("delFromGroup", this::removeUserGroup);
    this.commands.put("removeGroup", this::removeGroup);
    this.commands.put("upload", this::upload);
    this.scanner = scanner;
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(host);
    factory.setUsername(username);
    factory.setPassword(password);
    factory.setVirtualHost("/");
    this.connection = factory.newConnection();
  }

  public Channel newChatChannel(boolean is_publish_channel){
    Channel channel = null;
    try{
      channel = this.connection.createChannel();
      if(!is_publish_channel){
        channel.queueDeclare(user, false,   false,     false,       null);
        channel.queueDeclare(user + "_archives", false,   false,     false,       null);
        Consumer consumer = new DefaultConsumer(channel){
          public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
          throws IOException {
            receiveBytes(body);
          }
        };
        channel.basicConsume(user, true, consumer);
        channel.basicConsume(user + "_archives", true, consumer);
    }
    }
    catch(IOException e){
      System.out.println("Exceção ao tentar criar fila de usuário");
    }
    return channel;
  }

  public static String getChatData(){
    DateFormat diaMesAno = new SimpleDateFormat("dd/MM/yyyy");
    Date date = new Date();
    String data = diaMesAno.format(date);
    return data;
  }

  public static String getChatHora(){
    DateFormat horario = new SimpleDateFormat("HH:mm");
    Date date = new Date();
    String hora = horario.format(date);
    return hora;
  }

  public void checkAndSetUser(){
    do{
      System.out.print("User: ");
      user = scanner.nextLine();
    }while (!(user.length() > 0));
  }

  public void receiveBytes(byte[] body){//rotina de recebimento de mensagem
    try{
      ChatProto.Mensagem received = ChatProto.Mensagem.parseFrom(body);
      String emissor = received.getEmissor();
      if(emissor.equals(user)){//usuário não recebe mensagens que ele envia
        return;
      }
      String tipo = received.getConteudo().getTipo();
      String data = received.getData();
      String hora = received.getHora();
      String grupo = received.getGrupo();
      String mensagem = received.getConteudo().getCorpo().toStringUtf8();
      String nome_arquivo = received.getConteudo().getNome();
      if(!tipo.equals("text/plain")){//recebimento de arquivo
        if(!Files.exists(Paths.get(uploadsDirectory))){
          File pasta = new File(uploadsDirectory);
          pasta.mkdir();
        }
        FileOutputStream out = new FileOutputStream(uploadsDirectory + File.separator + nome_arquivo);
        out.write(mensagem.getBytes());
        out.close();
        System.out.println("\n(" + data + " às " + hora + ") Arquivo \"" + nome_arquivo + "\" recebido de @" + emissor + " !");
      }
      else if(!grupo.equals("")){//recebimento de mensagem de grupo
        System.out.println("\n(" + data + " às " + hora + ") " + emissor + "#" + grupo +" diz: " + mensagem);
      }
      else{//recebimento de mensagem de usuário
        System.out.println("\n(" + data + " às " + hora + ") " + emissor + " diz: " + mensagem);
      }
      System.out.print(userPrompt + ">> ");
    }
    catch (IOException e){
      System.out.println("Erro ao receber mensagem");
    }
  }

  private boolean userExists(String user, ChannelWrapper channelw){
    try{
      channelw.getChannel().queueDeclarePassive(user);
      return true;
    }
    catch(IOException e){
      System.out.println("Não há usuário " + "\"" + user + "\"");
      channelw.setChannel(newChatChannel(false));
      return false;
    }
  }

  private boolean groupExists(String group, ChannelWrapper channelw){
    try{
      channelw.getChannel().exchangeDeclarePassive(group);
      return true;
    }
    catch(IOException e){
      System.out.println("Não há grupo " + "\"" + group + "\"");
      channelw.setChannel(newChatChannel(false));
      return false;
    }
  }

  private void createGroup(String[] tokens, ChannelWrapper channelw){
    try{
      if(tokens.length != 2){
        System.out.println("Número de argumentos incorreto");
        return;
      }
      String group = tokens[1];
      channelw.getChannel().exchangeDeclare(group, "direct");
    }
    catch(IOException e){
      channelw.setChannel(newChatChannel(false));
      System.out.println(e);
    }
  }

  private void addUserGroup(String[] tokens, ChannelWrapper channelw){
    try{
      if(tokens.length != 3){
        System.out.println("Número de argumentos incorreto");
        return;
      }
      String user = tokens[1];
      String group = tokens[2];
      if(groupExists(group, channelw) && userExists(user, channelw)){
        Channel channel = channelw.getChannel();
        channel.queueBind(user, group, "message");
        channel.queueBind(user + "_archives", group, "archive");
      }
    }
    catch(IOException e){
      channelw.setChannel(newChatChannel(false));
      System.out.println(e);
    }
  }

  private void removeUserGroup(String[] tokens, ChannelWrapper channelw){
    try{
      if(tokens.length != 3){
        System.out.println("Número de argumentos incorreto");
        return;
      }
      String user = tokens[1];
      String group = tokens[2];
      if(userExists(user, channelw) && groupExists(group, channelw)){
        channelw.getChannel().queueUnbind(user, group, "");
      }
    }
    catch(IOException e){
      channelw.setChannel(newChatChannel(false));
      System.out.println(e);
    }
  }

  private void removeGroup(String[] tokens, ChannelWrapper channelw){
    try{
      if(tokens.length != 2){
        System.out.println("Número de argumentos incorreto");
        return;
      }
      String group = tokens[1];
      if(groupExists(group, channelw)){
        channelw.getChannel().exchangeDelete(group);
      }
    }
    catch(IOException e){
      channelw.setChannel(newChatChannel(false));
      System.out.println(e);
    }
  }

  private void sendGroupMessage(ChannelWrapper channelw, String message){//envio de mensagem text/plain para grupo
    try{
      if(!groupExists(sendGroup, channelw)) {
        sendGroup = "";
        userPrompt = "";
        return;
      }
      ChatProto.Conteudo.Builder c = ChatProto.Conteudo.newBuilder();
      c.setTipo("text/plain");
      c.setCorpo(com.google.protobuf.ByteString.copyFromUtf8(message));
      ChatProto.Conteudo content = c.build();
      ChatProto.Mensagem.Builder m = ChatProto.Mensagem.newBuilder();
      m.setEmissor(user);
      m.setData(getChatData());
      m.setHora(getChatHora());
      m.setConteudo(content);
      m.setGrupo(sendGroup);
      ChatProto.Mensagem mensagem = m.build();
      channelw.getChannel().basicPublish(sendGroup, "message", null,  mensagem.toByteArray());
    }
    catch(IOException e){
      channelw.setChannel(newChatChannel(false));
      System.out.println(e);
    }
  }

  private void sendUserMessage(ChannelWrapper channelw, String message){//envio de mensagem text/plain para usuário
    try{
      ChatProto.Conteudo.Builder c = ChatProto.Conteudo.newBuilder();
      c.setTipo("text/plain");
      c.setCorpo(com.google.protobuf.ByteString.copyFromUtf8(message));
      ChatProto.Conteudo content = c.build();
      ChatProto.Mensagem.Builder m = ChatProto.Mensagem.newBuilder();
      m.setEmissor(user);
      m.setData(getChatData());
      m.setHora(getChatHora());
      m.setConteudo(content);
      m.setGrupo("");
      ChatProto.Mensagem mensagem = m.build();
      channelw.getChannel().basicPublish("", sendUser, null,  mensagem.toByteArray());
    }
    catch(IOException e){
      channelw.setChannel(newChatChannel(false));
      System.out.println(e);
    }
  }
  private void upload(String[] tokens, ChannelWrapper channelw){
    if(tokens.length != 2){
      System.out.println("Número de argumentos incorreto");
      return;
    }
    if(sendUser.equals("") && sendGroup.equals("")){
      System.out.println("Não há destinatário");
      return;
    }
    Path path = Paths.get(tokens[1]);
    if(!Files.exists(path)){
      System.out.println("Arquivo \"" + path.toString() + "\" não existe");
      return;
    }
    ArchiveUpload upload_object = new ArchiveUpload();
    upload_object.setEmissor(user);
    upload_object.setReceptor(sendUser);//arquivos são publicados na fila de arquivos do receptor
    upload_object.setPath(path);
    upload_object.setGroup(sendGroup);
    upload_object.setUserPrompt(userPrompt);
    upload_object.setChannelWrapper(new ChannelWrapper(newChatChannel(true)));//cria um canal novo para enviar o arquivo
    Thread t = new Thread(upload_object);
    t.start();
    System.out.println("Enviando \"" + path.toString() + "\"" + " para " + userPrompt + ".");
  }

  public void chatLoop(){
    ChannelWrapper channelw = new ChannelWrapper(newChatChannel(false));//criando canal associado ao chatLoop
    String texto = "";
    sendUser = "";
    sendGroup = "";
    userPrompt = "";

    while(true){
      do{
        System.out.print(userPrompt + ">> ");
        texto = scanner.nextLine();
      }while(!(texto.length() != 0));

      char comparator = texto.charAt(0);
      if(comparator == '@'){//prompt individual
        String user = texto.substring(1, texto.length());
        if(userExists(user, channelw)){
          userPrompt = texto;
          sendUser = user;
          sendGroup = "";
        }
      }
      else if (comparator == '!'){//operações
        String tokens[] = texto.substring(1, texto.length()).split(" ");
        if(commands.containsKey(tokens[0])){
          BiConsumer<String[], ChannelWrapper> returned_method = commands.get(tokens[0]);
          returned_method.accept(tokens, channelw);
        }
        else{
          System.out.println("Não há operação correspondente");
        }
      }
      else if (comparator == '#'){//prompt de grupo
        String group = texto.substring(1, texto.length());
        if(groupExists(group, channelw)){
          userPrompt = texto;
          sendGroup = group;
          sendUser = "";
        }
      }
      else if (userPrompt.length() != 0){//envio de mensagem text/plain
        if(!sendUser.equals("")){
          sendUserMessage(channelw, texto);
        }
        else{
          sendGroupMessage(channelw, texto);
        }
      }
      else{
        System.out.println("Operação inválida");
      }
    }
  }  
  public static void main(String[] argv) throws Exception {
    Chat chat =  new Chat("34.207.225.196",
    "admin", "password", new Scanner(System.in));
    chat.checkAndSetUser();
    chat.chatLoop();
  }
}