package jp.juggler.ImgurMush;

import java.util.ArrayList;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import jp.juggler.ImgurMush.data.APIResult;
import jp.juggler.ImgurMush.helper.BaseActivity;

public class ActTest extends BaseActivity{
	final ActTest act = this;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Intent intent = getIntent();
		int n = intent.getIntExtra(PrefKey.EXTRA_TEST_MODE,0);
		
		if( n==PrefKey.TEST_ERROR_FORMAT ){
			setContentView(R.layout.act_test_error_detail);
			new AsyncTask<Void,Void,ArrayList<String> >(){
				@Override protected ArrayList<String> doInBackground(Void... params) {
					return APIResult.scanErrorLog(act);
				}
				@Override protected void onPostExecute(ArrayList<String> data) {
					if( data != null ){
						ArrayAdapter<String> adapter = new ArrayAdapter<String>(act,R.layout.lv_api_error,data);
						ListView listview = (ListView)findViewById(R.id.list);
						listview.setAdapter(adapter);
					}
				}
			}.execute();
		}else if( n == PrefKey.TEST_RATE_LIMIT ){
			setContentView(R.layout.act_test_rate_limit);
			final EditText etErrorDetail = (EditText)findViewById(R.id.text);
			new AsyncTask<Void,Void,StringBuilder>(){

				@Override
				protected StringBuilder doInBackground(Void... params) {
					return APIResult.dumpRateLimit(act);
				}

				@Override
				protected void onPostExecute(StringBuilder result) {
					if( result != null ){
						etErrorDetail.setText(result);
					}
				}
				
			}.execute();
		}
	}
}
