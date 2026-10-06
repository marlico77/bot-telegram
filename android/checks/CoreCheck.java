import br.com.marlico.bot.Core;
import java.util.*;
import java.net.*;
public class CoreCheck {
    static int checks=0;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void reject(Runnable task){boolean failed=false;try{task.run();}catch(IllegalArgumentException e){failed=true;}check(failed,"Invalid input accepted");}
    public static void main(String[] args){
        byte[] packet=Core.packet("02:11:22:33:44:55");
        check(packet.length==102,"Packet length");
        for(int i=0;i<6;i++)check((packet[i]&255)==255,"WOL preamble");
        byte[] mac={0x02,0x11,0x22,0x33,0x44,0x55};
        for(int i=6;i<102;i++)check(packet[i]==mac[(i-6)%6],"16 MAC repetitions");
        check(Core.mac("0211.2233.4455").equals("02:11:22:33:44:55"),"Cisco MAC");
        check(Core.mac("02-11-22-33-44-55").equals("02:11:22:33:44:55"),"Hyphen MAC");
        reject(()->Core.mac("02:11:22:33:44"));reject(()->Core.mac("ff:ff:ff:ff:ff:ff"));reject(()->Core.mac("00:00:00:00:00:00"));reject(()->Core.mac("02:11-22:33:44:55"));
        check(Core.users("1234567890,42").contains(1234567890L),"64-bit Telegram IDs");
        check(!Core.users("1234567890").contains(999L),"Other users rejected");
        reject(()->Core.users(""));reject(()->Core.users("12,"));reject(()->Core.users("-1"));reject(()->Core.users("3.0"));
        check(Core.ipv4("192.168.0.255").equals("192.168.0.255"),"Directed broadcast");
        check(Core.ipv4("255.255.255.255").equals("255.255.255.255"),"Global broadcast");
        check(Core.hostIpv4("192.168.1.50").equals("192.168.1.50"),"PC host IP");reject(()->Core.hostIpv4("192.168.0.255"));reject(()->Core.hostIpv4("255.255.255.255"));
        reject(()->Core.ipv4("256.0.0.1"));reject(()->Core.ipv4("localhost"));reject(()->Core.ipv4("127.0.0.1"));reject(()->Core.ipv4("224.0.0.1"));
        check(Core.port("9")==9,"Port");reject(()->Core.port("0"));reject(()->Core.port("65536"));
        String fake="123456789:abcdefghijklmnopqrstuvwxyz_123456789";Core.token(fake);reject(()->Core.token("incomplete"));
        Map<String,String> env=Core.env("\uFEFF# comment\r\nTELEGRAM_BOT_TOKEN='"+fake+"'\r\nPC_MAC_ADDRESS=02:11:22:33:44:55\nWOL_PORT=9\n");
        check(env.get("TELEGRAM_BOT_TOKEN").equals(fake),"Quoted token and BOM");check(env.get("WOL_PORT").equals("9"),"ENV parsed");
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {check(Core.tcpResponds(InetAddress.getLoopbackAddress(),server.getLocalPort()),"Active TCP host detection");int closed=server.getLocalPort();server.close();check(!Core.tcpResponds(InetAddress.getLoopbackAddress(),closed),"Closed TCP port cannot report host online");}catch(Exception e){throw new RuntimeException(e);}
        System.out.println("OK: "+checks+" verificações de Wake-on-LAN, IDs, .env e validação.");
    }
}
