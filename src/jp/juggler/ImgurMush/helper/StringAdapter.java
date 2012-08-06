package jp.juggler.ImgurMush.helper;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import jp.juggler.util.HelperEnvUI;

public class StringAdapter extends BaseAdapter{
	final HelperEnvUI env;
	final String strNoSelection;
	final String[] list;
	final int min_height;
	
	public StringAdapter(HelperEnvUI env, String strNoSelection, String[] list) {
		this.env = env;
		this.strNoSelection = strNoSelection;
		this.list = list;
		this.min_height = (int)(0.5f + env.density * 48 );
	}

	@Override
	public int getCount() {
		return list.length + (strNoSelection!=null?1:0);
	}

	@Override
	public Object getItem(int position) {
		if( strNoSelection != null ) --position;
		if(position<0 || position >= list.length ) return null;
		return list[position];
	}

	@Override
	public long getItemId(int position) {
		return 0;
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
			view = env.inflater.inflate(layout ,null );
			view.setTag( holder = new ViewHolder() );
			holder.tvName = (TextView)view;
			if(is_dropdown) view.setMinimumHeight(min_height);
		}else{
			holder = (ViewHolder)view.getTag();
		}
		try{
			String str = (String)getItem(position);
			if( str == null ) str = strNoSelection;
			holder.tvName.setText(str!=null? str :strNoSelection);
		}catch(Throwable ex){
			holder.tvName.setText("(error)");
		}
		return view;
	}


}
