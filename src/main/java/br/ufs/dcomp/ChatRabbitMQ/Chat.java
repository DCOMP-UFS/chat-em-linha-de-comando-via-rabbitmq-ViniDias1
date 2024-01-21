package br.ufs.dcomp.ExemploRabbitMQ;
import java.util.*;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class Chat {

  public static String user = "";
  
  public static String[] getDataHora(){
    String[] dataHora = new String[2];
    DateFormat diaMesAno = new SimpleDateFormat("dd/MM/yyyy");
    DateFormat horario = new SimpleDateFormat("HH:mm");
  
    Date date = new Date();
    String data = diaMesAno.format(date);
    dataHora[0] = data;
    String hora = horario.format(date);
    dataHora[1] = hora;

    
    return dataHora;
  }
  
  public static void chatLoop(String texto, Scanner scanner,Channel channel,Consumer consumer,String QUEUE_NAME) throws Exception{
     
    channel.basicConsume(QUEUE_NAME, true, consumer);
    
    String[] dataHora = getDataHora();
    String data = dataHora[0];
    String hora = dataHora[1];
    String sendTo;
    
    while(texto.startsWith("@")){
        
        Chat.user = texto;
        
        do{
          sendTo = Chat.user.substring(1, Chat.user.length());  
          System.out.print(Chat.user + ">> ");
          texto = scanner.nextLine();
          
          if(!texto.startsWith("@")){
              
            String mensagem = "(" + data + " às " + hora + ") " + sendTo + " diz: " + texto;
            channel.basicPublish("",sendTo, null,  mensagem.getBytes("UTF-8"));
          }
          
        }while(!texto.startsWith("@"));
    }  
  }
  
  

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("ec2-54-90-176-114.compute-1.amazonaws.com"); // Alterar
    factory.setUsername("admin"); // Alterar
    factory.setPassword("password"); // Alterar
    factory.setVirtualHost("/");
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();
   
    Scanner scanner = new Scanner(System.in);
    
    System.out.print("User: ");
    String user = scanner.nextLine();
    String QUEUE_NAME = user;
    channel.queueDeclare(QUEUE_NAME, false,   false,     false,       null);
    
    Consumer consumer = new DefaultConsumer(channel) {
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
      throws IOException {
        
        String texto = new String(body, "UTF-8");
        System.out.println("\n"+texto);
        System.out.print(Chat.user+">> ");

      }
    };
    String texto;
    
    System.out.print(">> ");
    texto = scanner.nextLine();
    
    chatLoop(texto,scanner,channel,consumer,QUEUE_NAME);
  }
  
}
