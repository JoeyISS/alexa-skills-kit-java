package barthelper;

import java.io.IOException;

import java.net.URL;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.OutputSpeech;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.SsmlOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

/**
 * This sample shows how to create a Lambda function for handling Alexa Skill requests
 * leverage an external API.  
 * 
 * This Skill retrieves information about the Bay Area Rapid Transit (BART) System.
 * 
 * Following could be an example of an interaction with Alexa:
 * 
 * <p>
 * User: "Alexa, ask BART Helper what the upcoming holidays are."
 * <p>
 * Alexa:"The upcoming 3 holidays are..."
 * 
 */

public class BartHelperSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(BartHelperSpeechlet.class);

    private static final String URL_PREFIX = "https://api.bart.gov/api/sched.aspx?json=y&";
    
    private static final String API_KEY = "MW9S-E7SL-26DU-VV8V";
    
    private static final String URL_DEPARTURES = "http://bart.crudworks.org/api/departures/";
    
    private static final String LOCATION_KEY = "Location";
    
    private static final String LOCAION_ID = "LOCATION";
    
    private static final int MAX_HOLIDAYS = 3;

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        return getWelcomeResponse();
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = intent.getName();

        if ("GetHolidaysIntent".equals(intentName)) {
        	try {
				return getBARTHolidays(intent);
			} catch (IOException e) {
				log.error("Holidays IO Error");
				e.printStackTrace();
				return getErrorResponse(intent);
			} catch (JSONException e) {
				log.error("Holidays JSON Error");
				e.printStackTrace();
				return getErrorResponse(intent);
			}
        } else if("GetTrainTimesIntent".equals(intentName)) {
        	try {
				return getBARTTrains(intent, session);
			} catch (IOException e) {
				log.error("Departures IO Error");
				e.printStackTrace();
				return getErrorResponse(intent);
			} catch (JSONException e) {
				log.error("Departures JSON Error");
				e.printStackTrace();
				return getErrorResponse(intent);
			}
        } else if("GetLastStationIntent".equals(intentName)) {
        	return getLastStation(intent, session);
        }
          else if ("AMAZON.HelpIntent".equals(intentName)) {
            return getHelpResponse(intent);
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            return getStopResponse(intent);
        } else if ("AMAZON.CancelIntent".equals(intentName)) {
            return getCancelResponse(intent);
        } else {
            throw new SpeechletException("Invalid Intent");
        }
		
    }
    
    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any session cleanup logic would go here
    }
    
    /**
     * Creates a {@code SpeechletResponse} for the GetHolidaysIntent.
     *
     * @param intent
     *            intent for the request
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    
    private SpeechletResponse getSpeechletResponse(String speechText, String repromptText,
            boolean isAskResponse) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("BART Service");
        card.setContent(speechText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        if (isAskResponse) {
            // Create reprompt
            PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
            repromptSpeech.setText(repromptText);
            Reprompt reprompt = new Reprompt();
            reprompt.setOutputSpeech(repromptSpeech);

            return SpeechletResponse.newAskResponse(speech, reprompt, card);

        } else {
            return SpeechletResponse.newTellResponse(speech, card);
        }
    }
    
    private SpeechletResponse getLastStation(Intent intent, final Session session){
    	
    	String speechText;
    	
    	boolean isAskedResponse = true;
    	
    	String lastStation = (String)session.getAttribute(LOCAION_ID);
    	
    	if(StringUtils.isNotEmpty(lastStation)) {
    		speechText = "The last station you asked was " + lastStation +".";
    	}else {
    		speechText = "Sorry, you haven't asked me about any station yet.";
    	}
    	
    	String repromptText = "   What would you ask me then?";
    	
    	speechText += repromptText;

        return getSpeechletResponse(speechText, repromptText, isAskedResponse);
    }
    
    private SpeechletResponse getBARTTrains(Intent intent, final Session session) throws IOException, JSONException{
    	
    	String trainURL = URL_DEPARTURES;
    	
    	boolean isAskedResponse = true;
    	
    	log.info("BART trainss URL: " + trainURL);
    	
    	URL url = new URL(trainURL);
    	Scanner scan = new Scanner(url.openStream());
    	String trainsOutput = new String();
    	while (scan.hasNext()) {
    		trainsOutput += scan.nextLine();
    	}
    	scan.close();
    	
    	String speechOutput = "";
    	
    	JSONArray locationsList = new JSONArray(trainsOutput);
    	
    	Map<String, Slot> slots = intent.getSlots();
    	Slot trainsSlot = slots.get(LOCATION_KEY);
    	String userLocation = trainsSlot.getValue();
    	
    	session.setAttribute(LOCAION_ID, userLocation);
    	
    	JSONArray theLocationInfo = new JSONArray();
    	
    	if(userLocation != null) {

    		for(int i = 0; i < locationsList.length(); i++) {
        		JSONObject o = (JSONObject) locationsList.get(i);
        		if(userLocation.equals(o.getString("name").toLowerCase())) {
        			theLocationInfo = o.getJSONArray("etd");
        			break;
        		}
        	}
    		
    		
    		
    		for(int i = 0; i < theLocationInfo.length(); i++) {
    			JSONObject o = theLocationInfo.getJSONObject(i);
    			JSONArray oA = o.getJSONArray("estimate");
    			JSONObject oB = oA.getJSONObject(0);
    			if(i == theLocationInfo.length() -1) {
    				speechOutput += "And the train going to " + o.getString("destination")
        			+ " leaves in " + oB.getInt("minutes") + " minutes from " 
        			+ " platform " + oB.getInt("platform") + ".";
    			}else {
    				speechOutput += "The train going to " + o.getString("destination")
        			+ " leaves in " + oB.getInt("minutes") + " minutes from " 
        			+ " platform " + oB.getInt("platform") + ". ";
    			}
    		}
    	}else {
    		speechOutput = "Sorry, the location you asked is not on the list.";
    	}

    	String repromptText = "   What would you ask me then?";
    	
    	speechOutput += repromptText;

        return getSpeechletResponse(speechOutput, repromptText, isAskedResponse);
    }
    
	private SpeechletResponse getBARTHolidays(Intent intent) throws IOException, JSONException {
    	
    	String command = "holiday";
    	String holidayURL = URL_PREFIX + "key=" + API_KEY + "&cmd=" + command;
    	
    	boolean isAskedResponse = true;
    	
    	log.info("BART Holidays URL: " + holidayURL);
    	
    	URL url = new URL(holidayURL);
    	Scanner scan = new Scanner(url.openStream());
    	String holidayOutput = new String();
    	while (scan.hasNext()) {
    		holidayOutput += scan.nextLine();
    	}
    	scan.close();
    	
    	// build a JSON object
    	JSONObject output = new JSONObject(holidayOutput);
    	
    	//get the results
    	JSONObject root = output.getJSONObject("root");
    	
    	JSONArray holidays = root.getJSONArray("holidays");
    	
    	JSONObject list = holidays.getJSONObject(0);
    	
    	JSONArray holidayList = list.getJSONArray("holiday");
    	
    	String speechOutput = "The upcoming " + MAX_HOLIDAYS + " holidays are: ";
    	for (int i=0; i < MAX_HOLIDAYS; i++) {
    		JSONObject o = (JSONObject) holidayList.get(i);
    		if (i == MAX_HOLIDAYS - 1) {
        		speechOutput = speechOutput + "and " + o.getString("name") +  " on " + o.getString("date") + ".";
    		} else {
    			speechOutput = speechOutput + o.getString("name") + " on " + o.getString("date") + ", ";
    		}
    	}
    	
    	String repromptText = "   What would you ask me then?";
    	
    	speechOutput += repromptText;

        return getSpeechletResponse(speechOutput, repromptText, isAskedResponse);
    	
	}
    
	/**
     * Creates a {@code SpeechletResponse} for the HelpIntent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
	private SpeechletResponse getHelpResponse(Intent intent) {
    	String speechOutput =
                "With Bart Helper, you can get"
                        + " information about the Bay Area Rapid Transit system."
                        + " For example, you could say what are the upcoming BART holidays? Or"
                        + " you can say when is the train from Orinda? "
                        + " You will also be able to locate the last station you asked Alexa."
                        + " Now, what would you like to know?";
                        

        String repromptText = "What would you like to know?";

        return newAskResponse(speechOutput, false, repromptText, false);
	}
	
	/**
     * Creates a {@code SpeechletResponse} when there is an error of any kind.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
	private SpeechletResponse getErrorResponse(Intent intent) {
    	
    	PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        outputSpeech.setText("I'm sorry... the BART information system seems to be down right now."
        						+ " Please try again later.");

        return SpeechletResponse.newTellResponse(outputSpeech);
		
	}

	/**
     * Creates a {@code SpeechletResponse} for the CancelIntent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
	private SpeechletResponse getCancelResponse(Intent intent) {
    	PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        outputSpeech.setText("Goodbye");

        return SpeechletResponse.newTellResponse(outputSpeech);
	}

	private SpeechletResponse getStopResponse(Intent intent) {
    	
    	PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        outputSpeech.setText("Goodbye");

        return SpeechletResponse.newTellResponse(outputSpeech);
	}


    /**
     * Function to handle the onLaunch skill behavior.
     * 
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse getWelcomeResponse() {
        String speechOutput = "Welcome to the BART Helper! What would you like to know?";
        // If the user either does not reply to the welcome message or says something that is not
        // understood, they will be prompted again with this text.
        String repromptText =
                "With BART Helper, you can get information about the Bay Area Rapid Transit system."
                        + " For example, you could say what are the upcoming BART holidays?"
                        + " Now, what would you like to know?";

        return newAskResponse(speechOutput, false, repromptText, false);
    }

    
    /**
     * Wrapper for creating the Ask response from the input strings.
     * 
     * @param stringOutput
     *            the output to be spoken
     * @param isOutputSsml
     *            whether the output text is of type SSML
     * @param repromptText
     *            the reprompt for if the user doesn't reply or is misunderstood.
     * @param isRepromptSsml
     *            whether the reprompt text is of type SSML
     * @return SpeechletResponse the speechlet response
     */
    private SpeechletResponse newAskResponse(String stringOutput, boolean isOutputSsml,
            String repromptText, boolean isRepromptSsml) {
        OutputSpeech outputSpeech, repromptOutputSpeech;
        if (isOutputSsml) {
            outputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) outputSpeech).setSsml(stringOutput);
        } else {
            outputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) outputSpeech).setText(stringOutput);
        }

        if (isRepromptSsml) {
            repromptOutputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) repromptOutputSpeech).setSsml(repromptText);
        } else {
            repromptOutputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) repromptOutputSpeech).setText(repromptText);
        }
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptOutputSpeech);
        return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
    }

}
