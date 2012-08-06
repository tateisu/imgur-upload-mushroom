package jp.juggler.ImgurMush;

import java.util.regex.Pattern;

import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.helper.StringAdapter;
import jp.juggler.ImgurMush.helper.UploadTargetManager;
import jp.juggler.ImgurMush.helper.UploadTargetManager.AlbumCreateCallback;
import jp.juggler.util.HelperEnvUI;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class DlgAlbumNew {
	final HelperEnvUI env;
	View root;
	AlertDialog dialog;
	View btnOk;
	TextView tvError;
	EditText etTitle;
	EditText etDesc;
	Spinner spPrivacy;
	Spinner spLayout;


	public DlgAlbumNew(HelperEnvUI _env,final UploadTargetManager target_manager,final ImgurAccount account){
		this.env = _env;
		this.root = env.inflater.inflate(R.layout.dlg_album_new,null);
		this.btnOk = root.findViewById(R.id.btnOk);
		this.tvError = (TextView)root.findViewById(R.id.tvError);
		this.etTitle = (EditText)root.findViewById(R.id.etTitle);
		this.etDesc = (EditText)root.findViewById(R.id.etDesc);
		this.spPrivacy = (Spinner)root.findViewById(R.id.spPrivacy);
		this.spLayout = (Spinner)root.findViewById(R.id.spLayout);
		
		etTitle.addTextChangedListener(new TextWatcher() {
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
			@Override public void beforeTextChanged(CharSequence s, int start, int count,int after) { }
			@Override public void afterTextChanged(Editable s) {
				check();
			}
		});
		etDesc.addTextChangedListener(new TextWatcher() {
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
			@Override public void beforeTextChanged(CharSequence s, int start, int count,int after) { }
			@Override public void afterTextChanged(Editable s) {
				check();
			}
		});
		
		spPrivacy.setAdapter(new StringAdapter(
			 env
			,env.getString(R.string.album_option_non_select)
			,env.resources.getStringArray(R.array.album_privacy_list)
		));
		spLayout.setAdapter(new StringAdapter(
			 env
			,env.getString(R.string.album_option_non_select)
			,env.resources.getStringArray(R.array.album_layout_list)
		));

		btnOk.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String desc = etDesc.getText().toString().trim();
				if( desc.length() <= 0 ) desc = null;
				//
				target_manager.create_album(account
					,etTitle.getText().toString().trim()
					,desc
					,(String)spPrivacy.getSelectedItem()
					,(String)spLayout.getSelectedItem()
					,new AlbumCreateCallback(){
						@Override public void onComplete() {
							dialog.dismiss();
						}
						@Override public void onError(String message) {
							tvError.setVisibility(View.VISIBLE);
							tvError.setText(message);
						}
					}
				);
			}
		});

		check();
	}

	public AlertDialog make_dialog(){
		this.dialog = new AlertDialog.Builder(env.context)
		.setCancelable(true)
		.setNegativeButton(R.string.cancel,null)
		.setTitle(R.string.album_add)
		.setView(root)
		.create();

		return dialog;
	}

	Pattern reTitle = Pattern.compile("\\A[A-Za-z0-9\\-_ ]+\\z");
	
	boolean check(){
		String s = etTitle.getText().toString().trim();
		if( s.length() <= 0 ){
			btnOk.setEnabled(false);
			tvError.setVisibility(View.VISIBLE);
			tvError.setText(env.getString(R.string.album_title_empty));
			return false;
		}else if( !reTitle.matcher(s).find() ){
			btnOk.setEnabled(false);
			tvError.setVisibility(View.VISIBLE);
			tvError.setText(env.getString(R.string.album_title_invalid_char));
			return false;
		}

		btnOk.setEnabled(true);
		tvError.setVisibility(View.INVISIBLE);
		return true;
	}
}
