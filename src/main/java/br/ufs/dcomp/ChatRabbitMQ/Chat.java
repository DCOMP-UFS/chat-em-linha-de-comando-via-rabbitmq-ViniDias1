package br.ufs.dcomp.ChatRabbitMQ;
import java.util.*;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class Chat {
  private String user;
  private String sendUser;
  private String sendGroup;
  private String userPrompt;
  private Connection connection;
  private Scanner scanner;

  public Chat(String host, String username, String password, Scanner scanner) throws Exception{
    this.scanner = scanner;
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(host);
    factory.setUsername(username);
    factory.setPassword(password);
    factory.setVirtualHost("/");
    this.connection = factory.newConnection();
  }

  public Channel newChatChannel() throws IOException{//cria um canal para o user do chat
    Channel channel = this.connection.createChannel();
    try{
      channel.queueDeclare(this.user, false,   false,     false,       null);
      Consumer consumer = new DefaultConsumer(channel) {
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
        throws IOException {
          receiveBytes(body);
        }
      };
      channel.basicConsume(this.user, true, consumer);

    }
    catch(IOException e){
      System.out.println("Exceção ao tentar criar fila de usuário");
    }
    return channel;
  }

  public String getChatData(){
    DateFormat diaMesAno = new SimpleDateFormat("dd/MM/yyyy");
    Date date = new Date();
    String data = diaMesAno.format(date);
    return data;
  }

  public String getChatHora(){
    DateFormat horario = new SimpleDateFormat("HH:mm");
    Date date = new Date();
    String hora = horario.format(date);
    return hora;
  }

  public void checkAndSetUser(){
    String _user;
    do{
      System.out.print("User: ");
      _user = this.scanner.nextLine();
    }while (!(_user.length() > 0));
    user = _user;
  }

  public void receiveBytes(byte[] body) throws IOException{//rotina de recebimento de mensagem
    ChatProto.Mensagem received = ChatProto.Mensagem.parseFrom(body);
    String emissor = received.getEmissor();
    if(emissor.equals(this.user)){//usuário não recebe mensagens que ele envia
      return;
    }
    String tipo = received.getConteudo().getTipo();
    String data = received.getData();
    String hora = received.getHora();
    String grupo = received.getGrupo();
    String mensagem = received.getConteudo().getCorpo().toStringUtf8();
    if(grupo != ""){
      System.out.print("\n(" + data + " às " + hora + ") " + emissor + "#" + grupo +" diz: " + mensagem);
    }
    else{
      System.out.print("\n(" + data + " às " + hora + ") " + emissor + " diz: " + mensagem);

    }
    System.out.print("\n" + userPrompt + ">> ");
  }

  private void createGroup(String group, Channel channel) throws IOException{
    try{
      channel.exchangeDeclare(group, "fanout");
    }
    catch(IOException e){
      System.out.println(e);
    }
  }

  private void addUserGroup(String user, String group, Channel channel) throws IOException{
    try{
      channel.queueBind(user, group, "");
    }
    catch(IOException e){
      System.out.println(e);
    }
  }

  private void removeUserGroup(String user, String group, Channel channel)throws IOException{
    try{
      channel.queueUnbind(user, group, "");
    }
    catch(IOException e){
      System.out.println(e);
    }
  }
  private void removeGroup(String group, Channel channel) throws IOException{
    try{
      channel.exchangeDelete(group);
    }
    catch(IOException e){
      System.out.println(e);
    }
  }

  private boolean userExists(String user, Channel channel) throws IOException{
    try{
      channel.queueDeclarePassive(user);
      return true;
    }
    catch(IOException e){
      System.out.println("Não há usuário " + "\"" + user + "\"");
      return false;
    }
  }

  private boolean groupExists(String group, Channel channel) throws IOException{
    try{
      channel.exchangeDeclarePassive(group);
      return true;
    }
    catch(IOException e){
      System.out.println("Não há grupo " + "\"" + group + "\"");
      return false;
    }
  }

  private boolean sendGroupMessage(Channel channel, String group, String message) throws IOException{//envio de mensagem text/plain para grupo
    try{
      if(!groupExists(group, channel)) {
        this.sendGroup = "";
        this.userPrompt = "";
        return false;
      }
      ChatProto.Conteudo.Builder c = ChatProto.Conteudo.newBuilder();
      c.setTipo("text/plain");
      c.setCorpo(com.google.protobuf.ByteString.copyFromUtf8(message));
      ChatProto.Conteudo content = c.build();
      ChatProto.Mensagem.Builder m = ChatProto.Mensagem.newBuilder();
      m.setEmissor(this.user);
      m.setData(getChatData());
      m.setHora(getChatHora());
      m.setConteudo(content);
      m.setGrupo(group);
      ChatProto.Mensagem mensagem = m.build();
      channel.basicPublish(group, "", null,  mensagem.toByteArray());
      return true;
    }
    catch(IOException e){
      System.out.println(e);
      return false;
    }
  }

  private boolean sendUserMessage(Channel channel, String user, String message) throws IOException{//envio de mensagem text/plain para usuário
    try{
      ChatProto.Conteudo.Builder c = ChatProto.Conteudo.newBuilder();
      c.setTipo("text/plain");
      c.setCorpo(com.google.protobuf.ByteString.copyFromUtf8(message));
      ChatProto.Conteudo content = c.build();
      ChatProto.Mensagem.Builder m = ChatProto.Mensagem.newBuilder();
      m.setEmissor(this.user);
      m.setData(getChatData());
      m.setHora(getChatHora());
      m.setConteudo(content);
      m.setGrupo("");
      ChatProto.Mensagem mensagem = m.build();
      channel.basicPublish("", sendUser, null,  mensagem.toByteArray());
      return true;
    }
    catch(IOException e){
      System.out.println(e);
      return false;
    }
  }

  public void chatLoop() throws Exception{
    String texto = null;
    Channel channel = newChatChannel();//criando canal associado ao chatLoop
    sendUser = "";
    sendGroup = "";
    userPrompt = "";

    while(true){
      do{
        System.out.print(userPrompt + ">> ");
        texto = this.scanner.nextLine();
      }while(!(texto.length() != 0));
      char comparator = texto.charAt(0);

      if(comparator == '@'){//prompt individual
        sendUser = texto.substring(1, texto.length());
        if(userExists(sendUser, channel)){
          userPrompt = texto;
          sendGroup = "";
        }
        else{
          channel = newChatChannel();
        }
      }
      else if (comparator == '!'){//operações com grupo
        String tokens[] = texto.substring(1, texto.length()).split(" ");
        int tokens_len = tokens.length;
        if(tokens[0].equals("addGroup") && tokens_len == 2){
          createGroup(tokens[1], channel);
          addUserGroup(user, tokens[1], channel);
        }
        else if(tokens[0].equals("addUser") && tokens_len == 3){
          if(userExists(tokens[1], channel) && groupExists(tokens[2], channel)){
            addUserGroup(tokens[1], tokens[2], channel);
          }
          else{
            channel = newChatChannel();
          }
        }
        else if(tokens[0].equals("delFromGroup") && tokens_len == 3){
          if(userExists(tokens[1], channel) && groupExists(tokens[2], channel)){
            removeUserGroup(tokens[1], tokens[2], channel);
          }
          else{
            channel = newChatChannel();
          }
        }
        else if(tokens[0].equals("removeGroup") && tokens_len == 2){
          if(groupExists(tokens[1], channel)){
            removeGroup(tokens[1], channel);
          }
          else{
            channel = newChatChannel();
          }
        }
        else{
          System.out.println("Não há operação de grupo correspondente");
        }
      }
      else if (comparator == '#'){//prompt de grupo
        sendGroup = texto.substring(1, texto.length());
        if(groupExists(sendGroup, channel)){
          userPrompt = texto;
          sendUser = "";
        }
        else{
          channel = newChatChannel();
        }
      }
      else if (userPrompt.length() != 0){//envio de mensagem text/plain
        if(sendUser != ""){
          if(!sendUserMessage(channel, sendUser, texto)){
            channel = newChatChannel();
          }
        }
        else if(sendGroup != ""){
          if(!sendGroupMessage(channel, sendGroup, texto)){
            channel = newChatChannel();
          }
        }
      }
      else{
        System.out.println("Operação inválida");
      }
    }
  }
  
  public static void main(String[] argv) throws Exception {
    Chat chat =  new Chat("ec2-54-80-245-137.compute-1.amazonaws.com",
    "admin", "password", new Scanner(System.in));
    chat.checkAndSetUser();
    chat.chatLoop();
  }
}