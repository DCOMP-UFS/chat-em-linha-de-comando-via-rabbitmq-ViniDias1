package br.ufs.dcomp.ChatRabbitMQ;
import com.rabbitmq.client.Channel;

public class ChannelWrapper{//envolve um objeto Channel já criado
    private Channel channel;

    public ChannelWrapper(Channel channel){
        this.channel = channel;
    }
    public void setChannel(Channel channel){
        this.channel = channel;
        if(this.channel == null){
        System.out.println("Erro na criação do channel");
        }
    }
    public Channel getChannel(){
        return channel;
    }
}