package jp.juggler.ImgurMush;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class ActAppInfo extends BaseActivity{

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_appinfo);
		
		Button btnAppName =(Button)findViewById(R.id.btnAppName);

		final String package_name = getPackageName();

		btnAppName.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try{
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://market.android.com/details?id="+package_name));  
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK );
					startActivity(intent);
				}catch(Throwable ex){
					ex.printStackTrace();
				}
				
			}
		});
		
		try{
			PackageInfo pin = getPackageManager().getPackageInfo(package_name, 0);
			btnAppName.setText(String.format("%s %s(%d)"
					,getString(R.string.app_name)
					,pin.versionName
					,pin.versionCode
			));
		}catch(NameNotFoundException ex){
			ex.printStackTrace();
		}
	}

}
