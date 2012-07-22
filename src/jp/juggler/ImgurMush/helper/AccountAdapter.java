package jp.juggler.ImgurMush.helper;

import jp.juggler.ImgurMush.BaseActivity;
import jp.juggler.ImgurMush.data.ImgurAccount;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class AccountAdapter extends BaseAdapter{
	final BaseActivity act;
	final Cursor cursor;
	final ImgurAccount.ColumnIndex colidx = new ImgurAccount.ColumnIndex();
	final String strNonSelect;
	boolean mDataValid = true;
	
	public AccountAdapter(BaseActivity act,Cursor _cursor,String strNonSelect){
		this.act = act;
		this.cursor = _cursor;
		this.strNonSelect = strNonSelect;
		
		cursor.registerContentObserver(new ContentObserver(act.ui_handler) {
			@Override
			public boolean deliverSelfNotifications() {
				return false;
			}

			@Override
			public void onChange(boolean selfChange) {
				mDataValid = cursor.requery();
			}
		});
		cursor.registerDataSetObserver(new DataSetObserver() {

			@Override
			public void onChanged() {
				mDataValid = true;
				notifyDataSetChanged();
			}

			@Override
			public void onInvalidated() {
				mDataValid = false;
				notifyDataSetInvalidated();
			}
			
		});
	}
	@Override
	public int getCount() {
		if( !mDataValid ) return 0;
		int n = cursor.getCount();
		return ( strNonSelect == null ? n : n+1 );
	}

	@Override
	public Object getItem(int idx) {
		if( !mDataValid ) return null;
		if( strNonSelect != null ) idx--;
		return ImgurAccount.loadFromCursor(cursor,colidx,idx);
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		return make_view( position,  view,  parent,android.R.layout.simple_spinner_item);
	}

	@Override
	public View getDropDownView(int position, View view,ViewGroup parent) {
		return make_view( position,  view,  parent,android.R.layout.simple_spinner_dropdown_item);
	}
	
	
	static class ViewHolder{
		TextView tvName;
	}

	private View make_view(int idx, View view, ViewGroup parent,int layout){
		ViewHolder holder;
		if(view!=null){
			holder = (ViewHolder)view.getTag();
		}else{
			view = act.inflater.inflate(layout ,null );
			view.setTag( holder = new ViewHolder() );
			holder.tvName = (TextView)view.findViewById(android.R.id.text1);
		}
		//
		ImgurAccount item = (ImgurAccount)getItem(idx);
		//
		if( item == null ){
			holder.tvName.setText(strNonSelect);
		}else{
			holder.tvName.setText(item.name);
		}
		//
		return view;
	}
	
	public void selectByName(AdapterView<?> listview, String name) {
		if( cursor.moveToFirst() ){
			int i=0;
			do{
				ImgurAccount item = ImgurAccount.loadFromCursor(cursor,colidx,i);
				if( item != null && name.equals(item.name) ){
					if( strNonSelect != null ) ++i;
					listview.setSelection(i);
					return;
				}
				++i;
			}while(cursor.moveToNext());
		}

		if( strNonSelect != null || cursor.getCount() > 0 ){
			listview.setSelection(0);
			return;
		}
	}
	


	public int findFromName(String target_account) {
		if(target_account == null ){
			if( strNonSelect !=null ) return 0;
		}else{
			int count = getCount();
			for(int idx=(strNonSelect !=null ? 1: 0 );idx<count;++idx){
				ImgurAccount item = (ImgurAccount)getItem(idx);
				if(item != null && item.name.equals(target_account) ) return idx;
			}
		}
		return 0;
	}
	

}
