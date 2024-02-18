package br.ufs.dcomp.ChatRabbitMQ;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import java.lang.Runnable;

public class ArchiveUpload implements Runnable{
  private Path path;
  private String emissor;
  private String receptor;
  private String group;
  private String userPrompt;
  private ChannelWrapper channelw;
  
  public ArchiveUpload(){

  }
  public void setPath(Path path){
    this.path = path;
  }
  public void setEmissor(String emissor){
    this.emissor = emissor;
  }
  public void setReceptor(String receptor){
    this.receptor = receptor;
  }
  public void setGroup(String group){
    this.group = group;
  }
  public void setUserPrompt(String userPrompt){
    this.userPrompt = userPrompt;
  }
  public void setChannelWrapper(ChannelWrapper channelw){
    this.channelw = channelw;
  }

  public void run(){
    try{
      byte[] body = Files.readAllBytes(path);
      ChatProto.Conteudo.Builder c = ChatProto.Conteudo.newBuilder();
      c.setTipo(Files.probeContentType(path));
      c.setCorpo(com.google.protobuf.ByteString.copyFrom(body));
      c.setNome(path.getFileName().toString());
      ChatProto.Conteudo content = c.build();
      ChatProto.Mensagem.Builder m = ChatProto.Mensagem.newBuilder();
      m.setEmissor(emissor);
      m.setData(Chat.getChatData());
      m.setHora(Chat.getChatHora());
      m.setConteudo(content);
      m.setGrupo(group.equals("") ? "" : group);
      ChatProto.Mensagem mensagem = m.build();
      if(!group.equals("")){
        channelw.getChannel().basicPublish(group, "archive", null,  mensagem.toByteArray());
      }
      else{
        channelw.getChannel().basicPublish("", receptor + "_archives", null,  mensagem.toByteArray());
      }
      System.out.println("\nArquivo \"" + path.toString() + "\" foi enviado para " + userPrompt + " !");
      System.out.print(userPrompt + ">> ");
      channelw.getChannel().close();
    }
    catch(IOException | TimeoutException e){
      System.out.println(e);
    }
  }
}