package br.ufs.dcomp.ChatRabbitMQ;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.rabbitmq.client.Channel;
import java.lang.Runnable;

public class ArchiveUpload implements Runnable{
  private String caminho;
  private Channel channel;
  private String emissor;
  private String receptor;
  private String group;
  
  public ArchiveUpload(String caminho, Channel channel, String emissor, String receptor, String group){
    this.caminho = caminho;
    this.channel = channel;
    this.emissor = emissor;
    this.receptor = receptor;
    this.group = group;
  
  }
  public void run(){
    try{
      Path path = Paths.get(caminho);
      if(!Files.exists(path)){
        System.out.println("Caminho/arquivo informado não existe");
        return;
      }
      String nome_arquivo = path.getFileName().toString();
      byte[] body = Files.readAllBytes(path);
      ChatProto.Conteudo.Builder c = ChatProto.Conteudo.newBuilder();
      c.setTipo(Files.probeContentType(path));
      c.setCorpo(com.google.protobuf.ByteString.copyFrom(body));
      c.setNome(nome_arquivo);
      ChatProto.Conteudo content = c.build();
      ChatProto.Mensagem.Builder m = ChatProto.Mensagem.newBuilder();
      m.setEmissor(emissor);
      m.setData(Chat.getChatData());
      m.setHora(Chat.getChatHora());
      m.setConteudo(content);
      m.setGrupo(group == "" ? "" : group);
      ChatProto.Mensagem mensagem = m.build();
      channel.basicPublish(group, receptor, null,  mensagem.toByteArray());
    }
    catch(IOException e){
      System.out.println(e);
    }

  }
}