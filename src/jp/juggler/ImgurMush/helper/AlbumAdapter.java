package jp.juggler.ImgurMush.helper;

import java.util.ArrayList;

import jp.juggler.ImgurMush.BaseActivity;
import jp.juggler.ImgurMush.data.ImgurAlbum;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class AlbumAdapter extends BaseAdapter{
	
	ArrayList<ImgurAlbum> album_list = new ArrayList<ImgurAlbum>();
	
	final BaseActivity act;
	String strNonSelection;
	
	public AlbumAdapter(BaseActivity act,String no_album) {
		this.act = act;
		this.strNonSelection = no_album;
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
		return make_view( position,  view,  parent,android.R.layout.simple_spinner_item);
	}

	@Override
	public View getDropDownView(int position, View view,ViewGroup parent) {
		return make_view( position,  view,  parent,android.R.layout.simple_spinner_dropdown_item);
	}
	
	static class ViewHolder {
		TextView tvName;
	}
	
	private View make_view(int position, View view, ViewGroup parent,int layout){
		ViewHolder holder;
		if(view==null){
			view = act.inflater.inflate(layout ,null );
			view.setTag( holder = new ViewHolder() );
			holder.tvName = (TextView)view.findViewById(android.R.id.text1);
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

	public void clear(String strCaption) {
		notifyDataSetInvalidated();
		strNonSelection = strCaption;
		album_list.clear();
		notifyDataSetChanged();
	}

	public void replace(ArrayList<ImgurAlbum> list, String strCaption) {
		notifyDataSetInvalidated();
		strNonSelection = strCaption;
		album_list = list;
		notifyDataSetChanged();
	}
}