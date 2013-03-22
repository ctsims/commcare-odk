/**
 * 
 */
package org.commcare.android.util;

import java.util.Vector;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.ArrayUtilities;
import org.javarosa.xpath.expr.XPathExpression;

/**
 * Basically Copy+Paste code from CCJ2ME that needs to be unified or re-indexed to somewhere more reasonable.
 * 
 * @author ctsims
 *
 */
public class CommCareUtil {

	public static FormInstance loadFixture(String refId, String userId) {
		IStorageUtilityIndexed<FormInstance> userFixtureStorage = CommCareApplication._().getUserStorage("fixture", FormInstance.class);
		IStorageUtilityIndexed<FormInstance> appFixtureStorage = CommCareApplication._().getAppStorage("fixture", FormInstance.class);
		
		Vector<Integer> userFixtures = userFixtureStorage.getIDsForValue(FormInstance.META_ID, refId);
		///... Nooooot so clean.
		if(userFixtures.size() == 1) {
			//easy case, one fixture, use it
			return (FormInstance)userFixtureStorage.read(userFixtures.elementAt(0).intValue());
			//TODO: Userid check anyway?
		} else if(userFixtures.size() > 1){
			//intersect userid and fixtureid set.
			//TODO: Replace context call here with something from the session, need to stop relying on that coupling
			
			Vector<Integer> relevantUserFixtures = userFixtureStorage.getIDsForValue(FormInstance.META_XMLNS, userId);
			
			if(relevantUserFixtures.size() != 0) {
				Integer userFixture = ArrayUtilities.intersectSingle(userFixtures, relevantUserFixtures);
				if(userFixture != null) {
					return (FormInstance)userFixtureStorage.read(userFixture.intValue());
				}
			}
		}
		
		//ok, so if we've gotten here there were no fixtures for the user, let's try the app fixtures.
		Vector<Integer> appFixtures = appFixtureStorage.getIDsForValue(FormInstance.META_ID, refId);
		Integer globalFixture = ArrayUtilities.intersectSingle(appFixtureStorage.getIDsForValue(FormInstance.META_XMLNS, ""), appFixtures);
		if(globalFixture != null) {
			return (FormInstance)appFixtureStorage.read(globalFixture.intValue());
		} else {
			return null;
		}
	}

	/**
	 * Used around to count up the degree of specificity for this reference
	 * 
	 * @param reference
	 * @return
	 */
	public static int countPreds(TreeReference reference) {
		int preds = 0;
		for(int i =0 ; i < reference.size(); ++i) {
			Vector<XPathExpression> predicates = reference.getPredicate(i);
			if(predicates != null) {
				preds += predicates.size();
			}
		}
		return preds;
	}
}
