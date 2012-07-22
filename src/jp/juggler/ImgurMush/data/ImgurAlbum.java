package jp.juggler.ImgurMush.data;

import org.json.JSONException;
import org.json.JSONObject;

public class ImgurAlbum implements Comparable<ImgurAlbum>{
	public ImgurAccount account;
	public String album_id;   // id 
	public String album_name; // title
	
	public ImgurAlbum() {
	}
	public ImgurAlbum(ImgurAccount account,JSONObject src) throws JSONException{
		this.account = account;
		album_id = src.getString("id");
		album_name = src.getString("title");
	}

	@Override
	public int compareTo(ImgurAlbum another) {
		int n;
		
		n = account.name.compareToIgnoreCase(another.account.name);
		if( n != 0 ) return n;
		
		n = album_name.compareToIgnoreCase(another.album_name);
		return n;
	}
	
} 
