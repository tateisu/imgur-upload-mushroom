package jp.juggler.util;

import java.util.HashSet;

public class LifeCycleManager {

	HashSet<LifeCycleListener> set = new HashSet<LifeCycleListener>();
	
	public void add( LifeCycleListener listener ){
		set.add(listener);
	}
	
	public void remove(LifeCycleListener listener ){
		set.remove(listener);
	}
	
	public void fire_onStart(){
		for( LifeCycleListener item :set ){
			try{ item.onStart(); }catch (Throwable ex) { ex.printStackTrace(); }
		}
	}
	
	public void fire_onResume(){
		for( LifeCycleListener item :set ){
			try{ item.onResume(); }catch (Throwable ex) { ex.printStackTrace(); }
		}
	}
	public void fire_onPause(){
		for( LifeCycleListener item :set ){
			try{ item.onPause(); }catch (Throwable ex) { ex.printStackTrace(); }
		}
	}
	public void fire_onStop(){
		for( LifeCycleListener item :set ){
			try{ item.onStop(); }catch (Throwable ex) { ex.printStackTrace(); }
		}
	}
	public void fire_onDestroy(){
		for( LifeCycleListener item :set ){
			try{ item.onDestroy(); }catch (Throwable ex) { ex.printStackTrace(); }
		}
	}
	public void fire_onRestart(){
		for( LifeCycleListener item :set ){
			try{ item.onRestart(); }catch (Throwable ex) { ex.printStackTrace(); }
		}
	}

	public void fire_onNewIntent(){
		for( LifeCycleListener item :set ){
			try{ item.onNewIntent(); }catch (Throwable ex) { ex.printStackTrace(); }
		}
	}
	
}
