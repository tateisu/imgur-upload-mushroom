package jp.juggler.ImgurMush.data;

import org.json.JSONException;
import org.json.JSONObject;

public class ImgurAlbum implements Comparable<ImgurAlbum>{
	public String account_name;
	public String album_id;   // id 
	public String album_name; // title
	
	public ImgurAlbum() {
	}
	public ImgurAlbum(String account_name,JSONObject src) throws JSONException{
		this.account_name = account_name;
		this.album_id = src.getString("id");
		this.album_name = src.getString("title");
	}

	@Override
	public int compareTo(ImgurAlbum another) {
		int n;
		
		n = account_name.compareToIgnoreCase(another.account_name);
		if( n != 0 ) return n;
		
		n = album_name.compareToIgnoreCase(another.album_name);
		return n;
	}
	
	public JSONObject toJSON() throws JSONException {
		JSONObject o = new JSONObject();
		o.put("id",album_id);
		o.put("title",album_name);
		return o;
	}
	
} 
