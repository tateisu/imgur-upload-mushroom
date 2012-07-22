package jp.juggler.ImgurMush;

import java.util.ArrayList;

import jp.juggler.ImgurMush.data.ImgurAccount;

import android.app.AlertDialog.Builder;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class Pref_DefaultAccount extends ListPreference{
	ContentResolver cr;
	Context context;
	
	public Pref_DefaultAccount(Context context) {
		super(context);
		this.context = context;
	}

	public Pref_DefaultAccount(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
	}


	void setup(){
		cr = context.getContentResolver();

		ArrayList<ImgurAccount> list = new ArrayList<ImgurAccount>();
		
		Cursor c = cr.query(ImgurAccount.meta.uri,null,null,null,ImgurAccount.COL_NAME+" asc");
		if( c!= null ){
			try{
				int colidx_id   = c.getColumnIndex(ImgurAccount.COL_ID);
				int colidx_name = c.getColumnIndex(ImgurAccount.COL_NAME);
				while( c.moveToNext() ){
					ImgurAccount item = new ImgurAccount();
					item.id = c.getLong(colidx_id);
					item.name = c.getString(colidx_name);
					list.add(item);
				}
			}finally{
				c.close();
			}
		}
		int count = list.size();
		String[] list_e = new String[2+count];
		String[] list_v = new String[2+count];
		list_e[0] = context.getString(R.string.account_last_used); 
		list_e[1] = context.getString(R.string.account_anonymous); 
		list_v[0] = "<>lastused";
		list_v[1] = "<>anonymous"; 
			
		for(int i=0;i<count;++i){
			ImgurAccount item = list.get(i);
			list_e[2+i]= item.name;
			list_v[2+i]= item.name;
		}
		setEntries( list_e );
		setEntryValues( list_v );
	}
	


	@Override
	protected void onPrepareDialogBuilder(Builder builder) {
		setup();
		super.onPrepareDialogBuilder(builder);
	}
	
}
