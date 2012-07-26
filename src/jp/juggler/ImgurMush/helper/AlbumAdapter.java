package jp.juggler.ImgurMush.helper;

import java.util.ArrayList;

import jp.juggler.ImgurMush.data.ImgurAlbum;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class AlbumAdapter extends BaseAdapter{
	
	ArrayList<ImgurAlbum> album_list = new ArrayList<ImgurAlbum>();
	
	final BaseActivity act;
	String strNonSelection;
	final int min_height;
	
	public AlbumAdapter(BaseActivity act,String no_album) {
		this.act = act;
		this.strNonSelection = no_album;
		
		float density = act.getResources().getDisplayMetrics().density;
		this.min_height = (int)(0.5f + density * 48 ); 
	}

	
	public void clear(String strCaption) {
		strNonSelection = strCaption;
		album_list.clear();
		notifyDataSetChanged();
	}

	public void replace(Iterable<ImgurAlbum> new_data, String strCaption) {
		strNonSelection = strCaption;
		album_list.clear();
		for( ImgurAlbum album: new_data){
			album_list.add(album);
		}
		notifyDataSetChanged();
	}

	public int findByName(String name) {
		final int offset = (strNonSelection==null?0:1);
		if( name != null ){
			for(int i=0,ie=album_list.size();i<ie;++i){
				ImgurAlbum album = album_list.get(i);
				if( name.equals(album.album_name ) ) return offset + i;
			}
		}
		return -1;
	}
	
	
	@Override
	public int getCount() {
		int n = album_list.size();
		return n + (strNonSelection==null?0:1);
	}

	@Override
	public long getItemId(int idx) {
		return 0;
	}

	@Override
	public Object getItem(int idx) {
		if(strNonSelection != null ) --idx;
		if( idx < 0 || idx >= album_list.size() ) return null;
		return album_list.get(idx);
	}


	@Override
	public View getView(int position, View view, ViewGroup parent) {
		return make_view( position,  view,  parent,android.R.layout.simple_spinner_item,false);
	}

	@Override
	public View getDropDownView(int position, View view,ViewGroup parent) {
		return make_view( position,  view,  parent,android.R.layout.simple_spinner_dropdown_item,true);
	}
	
	static class ViewHolder {
		TextView tvName;
	}
	
	private View make_view(int position, View view, ViewGroup parent,int layout, boolean is_dropdown){
		ViewHolder holder;
		if(view==null){
			view = act.inflater.inflate(layout ,null );
			view.setTag( holder = new ViewHolder() );
			holder.tvName = (TextView)view.findViewById(android.R.id.text1);
			if(is_dropdown) view.setMinimumHeight(min_height);
		}else{
			holder = (ViewHolder)view.getTag();
		}
		try{
			ImgurAlbum item = (ImgurAlbum)getItem(position);
			holder.tvName.setText( item == null ? this.strNonSelection : item.album_name );
		}catch(Throwable ex){
			holder.tvName.setText("(error)");
			
		}
		return view;
	}
}