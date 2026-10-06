import br.com.marlico.bot.Conversation;
import java.util.*;

public final class ConversationCheck {
    private static int checks;
    private static void check(boolean condition,String description) {
        checks++;
        if(!condition)throw new AssertionError(description);
    }
    private static long utc(int day,int hour,int minute,int second) {
        Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();c.set(2026,Calendar.OCTOBER,day,hour,minute,second);return c.getTimeInMillis();
    }
    private static void greeting(int day,int hour,int minute,int second,String expected) {
        check(Conversation.greeting(utc(day,hour,minute,second)).equals(expected+", Marlon! O que deseja?"),"Greeting at UTC "+hour+":"+minute+":"+second);
    }
    public static void main(String[] args) {
        TimeZone original=TimeZone.getDefault();
        try {
            // Deliberately use a wrong device timezone: the bot must use Brasilia anyway.
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            greeting(4,3,0,0,"Boa noite");       // 00:00
            greeting(4,7,59,59,"Boa noite");    // 04:59:59
            greeting(4,8,0,0,"Boa noite");      // 05:00
            greeting(4,8,0,59,"Boa noite");     // inclusive 05:00 minute
            greeting(4,8,1,0,"Bom dia");        // 05:01
            greeting(4,14,59,59,"Bom dia");     // 11:59:59
            greeting(4,15,0,0,"Boa tarde");     // 12:00
            greeting(4,20,59,59,"Boa tarde");   // 17:59:59
            greeting(4,21,0,0,"Boa noite");     // 18:00
            greeting(5,2,59,59,"Boa noite");    // 23:59:59, previous date in Brazil
            greeting(5,3,0,0,"Boa noite");      // next local midnight
        } finally {TimeZone.setDefault(original);}
        String session="valid-session";
        for(String message:new String[]{null,"","oi","/start","/ligar","wake:expired","menu:expired","wake_pc"})
            check(Conversation.action(message,session)==Conversation.Action.GREETING,"Messages and expired callbacks must not wake the PC");
        check(Conversation.action("menu:"+session,session)==Conversation.Action.MENU,"Menu opens options only");
        check(Conversation.action("wake:"+session,session)==Conversation.Action.WAKE,"Current wake button triggers wake");
        check(Conversation.action(Conversation.callback("status",session),session)==Conversation.Action.STATUS,"Status button opens PC metrics");
        check(Conversation.action(Conversation.callback("programs",session),session)==Conversation.Action.PROGRAMS,"Programs button lists running processes");
        check(Conversation.action(Conversation.callback("screen",session),session)==Conversation.Action.SCREEN,"Screen button requests a one-shot capture");
        check(Conversation.action(Conversation.callback("inventory",session),session)==Conversation.Action.INVENTORY,"Inventory button opens hardware and installed applications");
        check(Conversation.action(Conversation.callback("screen","expired"),session)==Conversation.Action.GREETING,"Expired screen request rejected");
        check(Conversation.action("wake:","")==Conversation.Action.GREETING,"Empty session rejected");
        check(Conversation.action("menu:",null)==Conversation.Action.GREETING,"Missing session rejected");
        check(Conversation.buttonLabel(Conversation.Action.GREETING).equals("Menu"),"Greeting has Menu button");
        check(Conversation.buttonLabel(Conversation.Action.MENU).equals("🖥️ Ligar PC"),"Menu has one wake option");
        check(Conversation.action(Conversation.buttonData(Conversation.Action.GREETING,session),session)==Conversation.Action.MENU,"Greeting -> Menu transition");
        check(Conversation.action(Conversation.buttonData(Conversation.Action.MENU,session),session)==Conversation.Action.WAKE,"Menu -> Wake transition");
        check(Conversation.action(Conversation.buttonData(Conversation.Action.GREETING,"expired"),session)==Conversation.Action.GREETING,"Old Menu cannot open current actions");
        check(Conversation.action(Conversation.buttonData(Conversation.Action.MENU,"expired"),session)==Conversation.Action.GREETING,"Old Wake cannot wake PC");
        System.out.println("OK: "+checks+" verificações de horários, fuso e navegação do Telegram.");
    }
}
