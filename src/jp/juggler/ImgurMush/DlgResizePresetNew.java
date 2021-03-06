package jp.juggler.ImgurMush;

import jp.juggler.ImgurMush.data.ResizePreset;
import jp.juggler.util.HelperEnvUI;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

public class DlgResizePresetNew {
	final HelperEnvUI env;
	View root;
	RadioGroup radio_group;
	AlertDialog dialog;
	View btnOk;
	TextView tvDesc;
	EditText etValue;
	int mode;
	int value;

	public static void show(HelperEnvUI env){
		new DlgResizePresetNew(env).setup();
		// TODO 自動生成されたメソッド・スタブ
	}
	public DlgResizePresetNew(HelperEnvUI _env){
		this.env = _env;
	}
	private void setup() {
		this.root = env.inflater.inflate(R.layout.dlg_resize_preset_new,null);
		this.tvDesc = (TextView)root.findViewById(R.id.tvDesc);
		this.radio_group = (RadioGroup)root.findViewById(R.id.radiogroup);
		this.etValue = (EditText)root.findViewById(R.id.etValue);
		radio_group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				check();
			}
		});
		etValue.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				check();
			}
		});
		
		this.dialog = new AlertDialog.Builder(env.context)
		.setCancelable(true)
		.setNegativeButton(R.string.cancel,null)
		.setPositiveButton(R.string.ok,new OnClickListener() {
			@Override
			public void onClick(DialogInterface di, int which) {
				env.dismiss(dialog);
				ResizePreset preset = new ResizePreset();
				preset.mode = mode;
				preset.value = value;
				preset.save(env.cr);
				
			}
		})
		.setTitle(R.string.resize_new_preset)
		.setView(root)
		.create();
		env.show_dialog(dialog);
		
		this.btnOk = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
		check();
	}

	boolean check(){
		int id = radio_group.getCheckedRadioButtonId();
		switch(id){
		case R.id.rbResizeMode0: this.mode = 0; break;
		case R.id.rbResizeMode1: this.mode = 1; break;
		case R.id.rbResizeMode2: this.mode = 2; break;
		default:
			btnOk.setEnabled(false);
			tvDesc.setVisibility(View.VISIBLE);
			tvDesc.setText(env.getString(R.string.resize_error_mode_not_selected));
			return false;
		}
		int v = -1;
		try{
			v = Integer.parseInt(etValue.getText().toString());
		}catch(Throwable ex){
			btnOk.setEnabled(false);
			tvDesc.setVisibility(View.VISIBLE);
			tvDesc.setText(env.getString(R.string.resize_error_value_not_number));
			return false;
		}
		if( v < 1 ){
			btnOk.setEnabled(false);
			tvDesc.setVisibility(View.VISIBLE);
			tvDesc.setText(env.getString(R.string.resize_error_value_too_small));
			return false;
		}
		int limit = (mode == 0 ? 99 : 2000);
		if( mode == 0 && v > limit ){
			btnOk.setEnabled(false);
			tvDesc.setVisibility(View.VISIBLE);
			tvDesc.setText(env.getString(R.string.resize_error_value_too_large,limit));
			return false;
		}
		this.value = v;
		btnOk.setEnabled(true);
		tvDesc.setVisibility(View.GONE);
		return true;
	}






}
