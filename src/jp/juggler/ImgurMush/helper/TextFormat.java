package jp.juggler.ImgurMush.helper;

import android.content.Context;
import android.text.format.DateUtils;

public class TextFormat {
	public static String formatByteSize(long s){
    	if( s >= 1024*1024*1024 ) return String.format("%.1fGB",s / (double)(1024*1024*1024 ) );
    	if( s >= 1024*1024 ) return String.format("%.1fMB",s / (double)(1024*1024 ) );
    	if( s >= 1024 ) return String.format("%.1fKB",s / (double)(1024 ) );
    	return String.format("%s byte",s);
    }
	
	public static String formatTime(Context c,long t){
		return DateUtils.formatDateTime(c,t,DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_24HOUR);
	}
}
