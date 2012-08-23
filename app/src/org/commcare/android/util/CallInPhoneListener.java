/**
 * 
 */
package org.commcare.android.util;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.commcare.android.models.User;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.Suite;
import org.commcare.suite.model.Text;
import org.commcare.util.CommCareSession;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author ctsims
 *
 */
public class CallInPhoneListener extends PhoneStateListener {
	
	private Context context;
	private AndroidCommCarePlatform platform;
	
	private Hashtable<String, String[]> cachedNumbers;
	private User user;
	
	private Toast currentToast;
	
	private Timer toastTimer;
	
	private boolean running = false;
	
	public CallInPhoneListener(Context context, AndroidCommCarePlatform platform, User user) {
		this.context = context;
		this.platform = platform;
		cachedNumbers = new Hashtable<String, String[]>();
		this.user = user;
		toastTimer = new Timer("toastTimer");
	}

	
	@Override
	public void onCallStateChanged(int state, String incomingNumber) {
		super.onCallStateChanged(state, incomingNumber);
		if(state == TelephonyManager.CALL_STATE_RINGING) {
			String caller = getCaller(incomingNumber);
			if(caller != null) {
				LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				View layout = inflater.inflate(R.layout.call_toast, null);
				TextView textView = (TextView)layout.findViewById(R.id.incoming_call_text);
				textView.setText("Incoming Call From: " + caller);
				
				currentToast = new Toast(context);
				currentToast.setDuration(Toast.LENGTH_LONG);
				
				currentToast.setView(layout);
				currentToast.setGravity(Gravity.BOTTOM, 0,0);
				running = true;
				startToastLoop();
			}
		} else {
			running = false;
		}
	}
	
	public void startToastLoop() {
		
		synchronized(toastTimer) {
			toastTimer.schedule(
					new TimerTask() {
						int runtimes = 0;
						public void run() {
							if(runtimes > 100 || running == false) {
								this.cancel();
							} else {
								runtimes++;
								currentToast.show();
							}
						}
					}, 0, 200);
		}
	}


	public void startCache() {
		
		AsyncTask<Void, Void, Void> loader = new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				try {
					synchronized(cachedNumbers) {
						Hashtable<String,Pair<String, TreeReference>> detailSources = new Hashtable<String,Pair<String, TreeReference>>();
						Set<Detail> details = new HashSet<Detail>();
						
						
						//To fan this out, we first need to find the appropriate long detail screens
						//then determine what nodeset to use to iterate over it
						
						//First, collect the details we need to use.
						for(Suite s : platform.getInstalledSuites() ){
							for(Entry e : s.getEntries().values()) {
								//We won't bother trying to handle the situation where there's more than one
								//thing to collect, just yet. In the future, we'll fan out the whole thing.
								if(e.getSessionDataReqs().size() !=1) {
									continue;
								}
								SessionDatum datum = e.getSessionDataReqs().firstElement();
								String detailId = datum.getLongDetail();
								
								for(String form : s.getDetail(detailId).getTemplateForms()) {
									if("phone".equals(form)) {
										//Found some numbers! 
										
										//Check to see if we've already got a detail for this 
										if(detailSources.containsKey(detailId)) {
											
											//Ok. So in the future we should possibly run all of the details
											//we can where ID's don't match, but for now, we'll stick with the smallest
											//set of predicates (most common use case is "Mine" v. "all" cases and such)
											TreeReference thisRef = datum.getNodeset();
											
											TreeReference existing = detailSources.get(detailId).second;
											
											if(CommCareUtil.countPreds(thisRef) < CommCareUtil.countPreds(existing)) {
												detailSources.put(detailId, new Pair(e.getCommandId(), thisRef));
											}
										}
										
										//Otherwise, grab the reference and save it.
										else {
											detailSources.put(detailId, new Pair(e.getCommandId(), datum.getNodeset()));
											details.add(s.getDetail(detailId));
										}
										//We don't need to worry about any other items in this detail, so finish up.
										break;
									}
								}
							}
						}
							
						//Ok, so now we have a set of details and the nodesets they use. Let's pull out some numbers
								
						//Go through each detail type one by one
						for(Detail d : details) {
							//Create an evaluation context (should only really need to handle the high level stuff)
							EvaluationContext ec = getEC(detailSources.get(d.getId()).first);
							
							TreeReference nodesetSource = detailSources.get(d.getId()).second;
							
							Vector<TreeReference> references =ec .expandReference(nodesetSource);
							
							Set<Integer> phoneIds = new HashSet<Integer>();
							String[] forms = d.getTemplateForms();
							for(int i = 0 ; i < forms.length ; ++i) {
								if("phone".equals(forms[i])) {
									//Get all the numbers we'll want
									phoneIds.add(i);
								}
							}
							
							Text[] templates = d.getTemplates();
							for(TreeReference r : references) {
								EvaluationContext childContext = new EvaluationContext(ec, r);
								//TODO: Generate a whole Session that could be used to start up form entry
								//based on this somehow?	
								
								String name = d.getTitle().evaluate(childContext);
								
								for(int i : phoneIds) {
									String number = templates[i].evaluate(childContext);
									if(number != "") {
										cachedNumbers.put(number, new String[] {name});
									}
								}
							}
						}
						System.out.println("Caching Complete");
						return null;
					}
				} catch(SessionUnavailableException sue) {
					//We got logged out in the middle of 
					return null;
				}
			}

			private EvaluationContext getEC(String commandId) {
				CommCareSession session = new CommCareSession(platform);
				session.setCommand(commandId);
				return session.getEvaluationContext(new CommCareInstanceInitializer(session));
			}
		};
		loader.execute();
	}
	
	public String getCaller(String incomingNumber) {
		synchronized(cachedNumbers) {
			for(String number : cachedNumbers.keySet()) {
				if(PhoneNumberUtils.compare(context, number, incomingNumber)) {
					return cachedNumbers.get(number)[0];
				}
			}
		}
		return null;
	}
	
	public Intent getDetailIntent(Context context, String incomingNumber) {
//		synchronized(cachedNumbers) {
//			for(String number : cachedNumbers.keySet()) {
//				if(PhoneNumberUtils.compare(context, number, incomingNumber)) {
//					String[] details = cachedNumbers.get(number);
//					
//					Intent i = new Intent(context, ReferenceDetailActivity.class);
//					i.putExtra(CommCareSession.STATE_COMMAND_ID, details[2]);
//					i.putExtra(CommCareSession.STATE_CASE_ID, details[1]);
//					i.putExtra(ReferenceDetailActivity.IS_DEAD_END, true);
//					return i;
//				}
//			}
//		}
		return null;
	}
}