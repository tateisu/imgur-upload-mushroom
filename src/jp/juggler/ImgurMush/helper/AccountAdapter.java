package jp.juggler.ImgurMush.helper;

import jp.juggler.ImgurMush.BaseActivity;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.util.LifeCycleListener;
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
	final ContentObserver content_observer;
	final DataSetObserver dataset_observer;
	
	boolean mDataValid = true;
	
	public AccountAdapter(BaseActivity act,String strNonSelect){
		this.act = act;
		this.strNonSelect = strNonSelect;
       
		this.content_observer = new ContentObserver(act.ui_handler) {
			@Override
			public boolean deliverSelfNotifications() {
				return false;
			}

			@Override
			public void onChange(boolean selfChange) {
				reload();
			}
		};
		this.dataset_observer = new DataSetObserver() {
		
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
			
		};

		this.cursor = act.cr.query(ImgurAccount.meta.uri,null,null,null,ImgurAccount.COL_NAME+" asc");
		cursor.registerContentObserver(content_observer);
		cursor.registerDataSetObserver(dataset_observer);
		
		act.lifecycle_manager.add(activity_listener);
	}
	
	LifeCycleListener activity_listener = new LifeCycleListener(){

		@Override
		public void onDestroy() {
			mDataValid = false;
			cursor.close();
		}

		@Override
		public void onResume() {
			reload();
		}
		
	};
	
	public void reload() {
		mDataValid = cursor.requery();
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
		int idx = findByName(name);
		listview.setSelection(idx < 0 ? 0 : idx);
	}
	
	private int findByName(String target_account) {
		int offset = ( strNonSelect !=null ? 1: 0);
		if( target_account != null ){
			if(mDataValid){
				if( cursor.moveToFirst() ){
					int i=0;
					do{
						ImgurAccount item = ImgurAccount.loadFromCursor(cursor,colidx);
						if( item != null && target_account.equals(item.name) ) return offset + i;
						++i;
					}while(cursor.moveToNext());
				}
			}
		}
		return -1;
	}
	

}
