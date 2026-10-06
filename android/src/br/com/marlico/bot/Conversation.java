package br.com.marlico.bot;

import java.util.Calendar;
import java.util.TimeZone;

/** Telegram conversation rules, independent of Android and the device's selected timezone. */
public final class Conversation {
    public enum Action { GREETING, MENU, WAKE, STATUS, PROGRAMS, SCREEN, INVENTORY }
    private Conversation() {}

    public static String greeting(long epochMillis) {
        Calendar clock=Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));
        clock.setTimeInMillis(epochMillis);
        int minute=clock.get(Calendar.HOUR_OF_DAY)*60+clock.get(Calendar.MINUTE);
        // The whole 05:00 minute belongs to the night; morning starts at 05:01.
        String salutation=minute<=300||minute>=1080?"Boa noite":minute<720?"Bom dia":"Boa tarde";
        return salutation+", Marlon! O que deseja?";
    }

    public static Action action(String callbackData,String session) {
        if(session==null||session.isEmpty())return Action.GREETING;
        if(callbackData==null)return Action.GREETING;
        String suffix=":"+session;
        if(callbackData.equals("menu"+suffix))return Action.MENU;
        if(callbackData.equals("wake"+suffix))return Action.WAKE;
        if(callbackData.equals("status"+suffix))return Action.STATUS;
        if(callbackData.equals("programs"+suffix))return Action.PROGRAMS;
        if(callbackData.equals("screen"+suffix))return Action.SCREEN;
        if(callbackData.equals("inventory"+suffix))return Action.INVENTORY;
        return Action.GREETING;
    }

    public static String buttonLabel(Action screen) { return screen==Action.MENU?"🖥️ Ligar PC":"Menu"; }
    public static String buttonData(Action screen,String session) { return (screen==Action.MENU?"wake:":"menu:")+session; }
    public static String callback(String action,String session){return action+":"+session;}
}
