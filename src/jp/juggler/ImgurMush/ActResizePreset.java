package jp.juggler.ImgurMush;

import jp.juggler.ImgurMush.data.ResizePreset;
import jp.juggler.ImgurMush.helper.BaseActivity;
import jp.juggler.ImgurMush.helper.ResizePresetAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

public class ActResizePreset extends BaseActivity{

	final ActResizePreset act = this;
	ResizePresetAdapter resize_preset_adapter;
	ListView listview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_resize_preset);
		setResult(RESULT_CANCELED);

		listview = (ListView)findViewById(R.id.list);

		resize_preset_adapter = new ResizePresetAdapter(env,getString(R.string.resize_disabled),getString(R.string.resize_new_preset));
		listview.setAdapter(resize_preset_adapter);
		listview.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View arg1,int idx, long arg3) {
				final ResizePreset preset = (ResizePreset)resize_preset_adapter.getItem(idx);
				if( preset != null){
					env.confirm(
						preset.makeTitle(env)
						,env.getString(R.string.resize_preset_delete)
						,true
						,new Runnable() {
							@Override public void run() {
								preset.delete(env.cr);
							}
						}
						,null
					);
					return true;
				}
				return false;
			}
		});
		listview.setOnItemClickListener(new OnItemClickListener() {
			@Override public void onItemClick(AdapterView<?> arg0, View arg1, int idx,long arg3) {
				ResizePreset preset = (ResizePreset)resize_preset_adapter.getItem(idx);
				if( preset!=null ){
					Intent intent = new Intent();
					intent.putExtra(PrefKey.EXTRA_RESIZE_PRESET_MODE,preset.mode);
					intent.putExtra(PrefKey.EXTRA_RESIZE_PRESET_VALUE,preset.value);
					setResult(RESULT_OK,intent);
					finish();
				}else if( idx <= 0 ){
					Intent intent = new Intent();
					intent.putExtra(PrefKey.EXTRA_RESIZE_PRESET_MODE,-1);
					setResult(RESULT_OK,intent);
					finish();
				}else{
					DlgResizePresetNew.show(env);
				}
			}
		});
	}

}
