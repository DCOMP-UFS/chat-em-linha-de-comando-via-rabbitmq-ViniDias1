package br.ufs.dcomp.ChatRabbitMQ;
import java.util.*;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class Chat {
  private static String sendTo;
  private static String texto;
  private static String user;
  private static String userPrompt;
  
  public static String getData(){
    DateFormat diaMesAno = new SimpleDateFormat("dd/MM/yyyy");
    Date date = new Date();
    String data = diaMesAno.format(date);
    return data;

  }
  public static String getHora(){
    DateFormat horario = new SimpleDateFormat("HH:mm");
    Date date = new Date();
    String hora = horario.format(date);
    return hora;
  }
  public static void setUser(String new_user){
    user = new_user;
  }
  
  public static void chatLoop(Scanner scanner, Channel channel) throws Exception{
    do{
      System.out.print(">> ");
      texto = scanner.nextLine();
    }while(!texto.startsWith("@"));
    userPrompt = texto;
    sendTo = userPrompt.substring(1, userPrompt.length());
    while(true){
      System.out.print(userPrompt + ">> ");
      texto = scanner.nextLine();
      if(texto.startsWith("@")) {
        userPrompt = texto;
        sendTo = userPrompt.substring(1, userPrompt.length());
      }
      else{
        String mensagem = "(" + getData() + " às " + getHora() + ") " + Chat.user + " diz: " + texto;
        channel.basicPublish("",sendTo, null,  mensagem.getBytes("UTF-8"));
      }
    }
  }
  
  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("ec2-54-224-2-21.compute-1.amazonaws.com"); // Alterar
    factory.setUsername("admin"); // Alterar
    factory.setPassword("password"); // Alterar
    factory.setVirtualHost("/");
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();
    Scanner scanner = new Scanner(System.in);
    System.out.print("User: ");
    String user = scanner.nextLine();
    String QUEUE_NAME = user;
    Chat.setUser(user);
    channel.queueDeclare(QUEUE_NAME, false,   false,     false,       null);
    Consumer consumer = new DefaultConsumer(channel) {
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
      throws IOException {
        String texto = new String(body, "UTF-8");
        System.out.println("\n"+texto);
        System.out.print(userPrompt+">> ");

      }
    };
    channel.basicConsume(QUEUE_NAME, true, consumer);
    chatLoop(scanner, channel);
  }
}