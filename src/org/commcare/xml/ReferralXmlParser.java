/**
 * 
 */
package org.commcare.xml;

import java.io.IOException;
import java.util.Date;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.models.Referral;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.data.xml.TransactionParser;
import org.commcare.xml.util.InvalidStructureException;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;

/**
 * @author ctsims
 *
 */
public class ReferralXmlParser extends TransactionParser<Referral> {
	String caseId;
	Date created;
	Context context;
	
	IStorageUtilityIndexed storage;

	public ReferralXmlParser(KXmlParser parser, String caseId, Date created, Context context) {
		super(parser, "referral", null);
		this.caseId = caseId;
		this.context = context;
		this.created = created;
	}
	
	public Referral parse() throws InvalidStructureException, IOException, XmlPullParserException, SessionUnavailableException {
		this.checkNode("referral");
		
		//parse (with verification) the next tag
		this.nextTag("referral_id");
		String refId = parser.nextText().trim();
		
		//temporary
		Date followup = created;
		
		//Now look for actions
		while(this.nextTagInBlock("referral")) {
			
			String action = parser.getName().toLowerCase();
			
			if(action.equals("followup_date")) {
				String followupDate = parser.nextText().trim();
				followup = DateUtils.parseDate(followupDate);
			}
			
			if(action.equals("open")) {
				this.getNextTagInBlock("open");
				checkNode("referral_types");
				String referralTypes = parser.nextText().trim();
				for(Object s : DateUtils.split(referralTypes, " ", true)) {
					Referral pr = new Referral((String)s, created, refId, caseId, followup);
					
					//If there's already a referral we need to duplicate the ID to overwrite the record.
					Referral r = retrieve(refId, (String)s);
					if(r != null) {
						pr.setID(r.getID());
					}
					commit(pr);
				}
				if(this.nextTagInBlock("open")) {
					throw new InvalidStructureException("Expected </open>, found " + parser.getName(), parser);
				}
			} else if(action.equals("update")) {
				this.getNextTagInBlock("update");
				checkNode("referral_type");
				String refType = parser.nextText().trim();
				Referral r = retrieve(refId, refType);
				r.setDateDue(followup);
				if(this.nextTagInBlock("update")) {
					String dateClosed = parser.nextText().trim();
					//TODO: Hmmm, see if we need to do anything here.
					//Date closed = DateUtils.parseDate(dateClosed);
					r.close();
					commit(r);
					
					if(this.nextTagInBlock("update")) {
						throw new InvalidStructureException("Expected </update>, found " + parser.getName(), parser);
					}
				}
			}
		}
		
		//This parser doesn't just deal with one object....
		return null;
	}

	public void commit(Referral parsed) throws IOException, SessionUnavailableException {
		try {
			storage().write(parsed);
		} catch (StorageFullException e) {
			e.printStackTrace();
			throw new IOException("Storage full while trying to write patient referral!");
		}
	}

	public Referral retrieve(String entityId, String type) throws SessionUnavailableException{
		IStorageUtilityIndexed storage = (IStorageUtilityIndexed)CommCareApplication._().getStorage(Referral.STORAGE_KEY, Referral.class);
		for(Object i : storage.getIDsForValue(Referral.REFERRAL_ID, entityId)) {
			Referral r = (Referral)storage.read(((Integer)i).intValue());
			if(r.getType().equals(type)) {
				return r;
			}
		}
		return null;
	}
	
	private IStorageUtilityIndexed storage() throws SessionUnavailableException {
		if(storage == null) {
			storage =  CommCareApplication._().getStorage(Referral.STORAGE_KEY, Referral.class);
		}
		return storage;
	}
}