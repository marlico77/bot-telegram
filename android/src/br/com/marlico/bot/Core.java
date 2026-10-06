package br.com.marlico.bot;

import java.io.IOException;
import java.net.*;
import java.util.*;

/** Pure Java protocol and validation code, also exercised outside Android. */
public final class Core {
    private Core() {}
    public static String mac(String value) {
        String raw = value.trim();
        if (!raw.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}|([0-9a-f]{2}-){5}[0-9a-f]{2}|[0-9a-f]{12}|([0-9a-f]{4}\\.){2}[0-9a-f]{4}"))
            throw new IllegalArgumentException("MAC inválido. Use o endereço da placa de rede do seu PC, no formato AA:BB:CC:DD:EE:FF.");
        String hex = raw.replace(":", "").replace("-", "").replace(".", "").toUpperCase(Locale.ROOT);
        if (hex.equals("000000000000") || (Integer.parseInt(hex.substring(0, 2), 16) & 1) != 0)
            throw new IllegalArgumentException("Informe o MAC de uma placa de rede do PC.");
        StringBuilder out = new StringBuilder();
        for (int i=0;i<12;i+=2) { if(i>0) out.append(':'); out.append(hex, i, i+2); }
        return out.toString();
    }
    public static byte[] packet(String address) {
        String hex = mac(address).replace(":", "");
        byte[] packet = new byte[102];
        Arrays.fill(packet, 0, 6, (byte)255);
        for(int r=0;r<16;r++) for(int b=0;b<6;b++)
            packet[6+r*6+b] = (byte)Integer.parseInt(hex.substring(b*2,b*2+2),16);
        return packet;
    }
    public static Set<Long> users(String value) {
        Set<Long> ids = new LinkedHashSet<>();
        for(String item : value.split(",", -1)) {
            try { long id=Long.parseLong(item.trim()); if(id<=0) throw new NumberFormatException(); ids.add(id); }
            catch(NumberFormatException ex) { throw new IllegalArgumentException("Informe seu ID numérico do Telegram. Separe vários IDs por vírgula."); }
        }
        if(ids.isEmpty()) throw new IllegalArgumentException("Informe ao menos um usuário autorizado.");
        return ids;
    }
    public static String ipv4(String value) {
        String ip=value.trim(); String[] parts=ip.split("\\.", -1);
        if(parts.length!=4) throw new IllegalArgumentException("Broadcast inválido. Use um IPv4, como 255.255.255.255.");
        for(String p:parts) {
            if(!p.matches("[0-9]{1,3}") || Integer.parseInt(p)>255) throw new IllegalArgumentException("Broadcast inválido.");
        }
        int first=Integer.parseInt(parts[0]);
        if(first==0 || first==127 || (first>=224 && !ip.equals("255.255.255.255")))
            throw new IllegalArgumentException("Informe o broadcast da sua rede local.");
        return ip;
    }
    public static String hostIpv4(String value) {
        String ip=ipv4(value);
        String[] octets=ip.split("\\.");
        if(ip.equals("255.255.255.255")||Integer.parseInt(octets[3])==255||Integer.parseInt(octets[3])==0)
            throw new IllegalArgumentException("Use o IP do PC, não o endereço de broadcast da rede.");
        return ip;
    }
    public static int port(String value) {
        try { int p=Integer.parseInt(value.trim()); if(p<1||p>65535) throw new NumberFormatException(); return p; }
        catch(NumberFormatException e) { throw new IllegalArgumentException("A porta precisa estar entre 1 e 65535."); }
    }
    /** Best-effort live presence check: ICMP echo or a successful TCP handshake. */
    public static boolean hostResponds(String address,int port) throws IOException {
        InetAddress target=InetAddress.getByName(hostIpv4(address));
        try { if(target.isReachable(650))return true; } catch(IOException ignored) {}
        return tcpResponds(target,port);
    }
    public static boolean tcpResponds(InetAddress target,int port) throws IOException {
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress(target,port),1100);
            return true;
        } catch(SocketTimeoutException|ConnectException ignored) { return false; }
    }
    public static void token(String value) {
        if(!value.matches("[0-9]{5,16}:[A-Za-z0-9_-]{30,80}")) throw new IllegalArgumentException("O token está incompleto. Importe seu .env ou copie o token do BotFather.");
    }
    public static Map<String,String> env(String text) {
        Map<String,String> out=new HashMap<>();
        for(String raw:text.replace("\uFEFF", "").split("\\r?\\n")) {
            String line=raw.trim(); if(line.startsWith("#")||line.isEmpty()) continue;
            int eq=line.indexOf('='); if(eq<=0) continue;
            String key=line.substring(0,eq).trim(), value=line.substring(eq+1).trim();
            if(value.length()>=2 && ((value.startsWith("\"")&&value.endsWith("\""))||(value.startsWith("'")&&value.endsWith("'")))) value=value.substring(1,value.length()-1);
            out.put(key,value);
        }
        return out;
    }
    public static void sendWake(String mac, String broadcast, int port) throws IOException {
        byte[] data=packet(mac);
        Set<InetAddress> destinations=new LinkedHashSet<>();
        destinations.add(InetAddress.getByName(ipv4(broadcast)));
        if(broadcast.equals("255.255.255.255")) {
            try {
                Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
                if(interfaces!=null) while(interfaces.hasMoreElements()) {
                    NetworkInterface nic=interfaces.nextElement();
                    if(!nic.isUp()||nic.isLoopback()) continue;
                    for(InterfaceAddress a:nic.getInterfaceAddresses()) if(a.getBroadcast()!=null) destinations.add(a.getBroadcast());
                }
            } catch(SocketException ignored) { /* Global broadcast remains available on restrictive firmware. */ }
        }
        boolean sent=false; IOException last=null;
        try(DatagramSocket socket=new DatagramSocket()) {
            socket.setBroadcast(true);
            for(InetAddress address:destinations) {
                try { for(int i=0;i<3;i++) socket.send(new DatagramPacket(data,data.length,address,port)); sent=true; }
                catch(IOException e) { last=e; }
            }
        }
        if(!sent) throw last!=null?last:new IOException("Sem conexão com a rede local.");
    }
}
