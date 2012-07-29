package jp.juggler.ImgurMush.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import jp.juggler.ImgurMush.data.ImgurAlbum;

public class AlbumList {
	private HashMap<String,ImgurAlbum> album_map = new HashMap<String,ImgurAlbum>();
	private ArrayList<ImgurAlbum> album_list = new ArrayList<ImgurAlbum>();
	
	public int from;
	public static final int FROM_RESPONSE = 1; // レスポンスから
	public static final int FROM_CACHE = 2; // パーマネントキャッシュから
	public static final int FROM_ERROR = 3; // エラーレスポンス
	

	public void update_map() {
		Collections.sort(album_list);
		album_map.clear();
		for( ImgurAlbum album :  album_list ){
			album_map.put( album.album_id , album );
		}
	}

	public void add(ImgurAlbum album){
		album_list.add(album);
	}

	public ImgurAlbum get(String album_id) {
		return album_id==null? null : album_map.get(album_id);
	}
	
	public Iterable<ImgurAlbum> iter() {
		return album_list;
	}

	public int size() {
		return album_list.size();
	}

}
