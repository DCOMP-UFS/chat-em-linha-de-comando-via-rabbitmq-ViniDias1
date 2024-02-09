package br.ufs.dcomp.ChatRabbitMQ;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.rabbitmq.client.Channel;
import java.lang.Thread;

public class ArchiveUpload extends Thread{
  private void run(String caminho, Channel channel, String user, String group) throws IOException{
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
      ChatProto.Conteudo content = c.build();
      ChatProto.Mensagem.Builder m = ChatProto.Mensagem.newBuilder();
      m.setEmissor(user);
      m.setData(Chat.getChatData());
      m.setHora(Chat.getChatHora());
      m.setConteudo(content);
      m.setGrupo(group == "" ? "" : group);
      ChatProto.Mensagem mensagem = m.build();
      channel.basicPublish(group, user, null,  mensagem.toByteArray());
    }
    catch(IOException e){
      System.out.println(e);
    }

  }
}