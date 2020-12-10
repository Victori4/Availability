import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class Filter implements MqttCallback {

    private final static ExecutorService THREAD_POOL = Executors.newSingleThreadExecutor();

    private final IMqttClient middleware;

    public Filter(String userid, String broker) throws MqttException {
        middleware = new MqttClient(broker, userid);
        middleware.connect();
        middleware.setCallback(this);
    }

    public static void main(String[] args) throws MqttException, InterruptedException {
        Filter s = new Filter("test-filter", "tcp://localhost:1883");
        s.subscribeToMessages("BookingResponse");
        s.subscribeToMessages("BookingRequest");
        s.subscribeToMessages("Dentists");
    }

    private void subscribeToMessages(String sourceTopic) {
        THREAD_POOL.submit(() -> {
            try {
                middleware.subscribe(sourceTopic);
            } catch (MqttSecurityException e) {
                e.printStackTrace();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void connectionLost(Throwable throwable) {
        System.out.println("Connection lost!");
        while (middleware.isConnected() == false) {



            // reestablish connection lost? PLan b
            try {
                Thread.sleep(3000);
                System.out.println("Reconnecting..");
                middleware.reconnect();

                // middleware.setCallback(this);  // unclear what this method does (still works with or without).
            } catch (Exception e) {
                throwable.getMessage();
            }
        }
        System.out.println("Connection to broker reestablished!");
        System.out.println(middleware.isConnected());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    /**
     * For simplicity we here assume that the message payload is equal to the filter
     * sink. It is important that the sink is part of the message, as the filter
     * itself is stateless. It does not know where it is (subscription topic, set on
     * instantiation), nor where it publishes (publishing topic, set per message).
     */
    @Override
    public void messageArrived(String topic, MqttMessage incoming) throws Exception {
        ReceivedBooking receivedBooking = makeReceivedBooking(incoming); // We take in the booking request and
        // create a new received booking item with certain fields
        String sinkTopic = extractTopic(incoming);

        dump(receivedBooking, sinkTopic);
    }


    private String extractTopic(MqttMessage incoming) {
        return incoming.toString();
    }

    private void dump(ReceivedBooking receivedBooking, String sinkTopic) throws MqttPersistenceException, MqttException {
        MqttMessage outgoing = new MqttMessage();
        outgoing.setPayload(receivedBooking.toString().getBytes());
        middleware.publish(sinkTopic, outgoing);
    }

    public void checkAvailability(ReceivedBooking requestBooking, ArrayList<Dentist> dentists,
                                  ArrayList<Booking> bookings) {
        // Needs to take in data from Dentist, Booking and booking response from Frontend and check them
        // Uses dentist id from booking response, iterates through dentist array for matching dentist id
        // if requestBooking.dentistid == bookings.dentistid AND resp.time!=bookings.time then booking OK
        // if requestBooking.dentistid == bookings.dentistid AND resp.time==bookings.time, then check dentists.dentists number
        // if dentists.dentists number > number of copies of bookings.dentistid + bookings.time then booking OK

        //make variables storing info from request booking
        long dentist = requestBooking.dentistid;

        for (int i = 0; i < bookings.size(); i++) {
            if ((requestBooking.dentistid == bookings.get(i).getDentistid()) && (requestBooking.time != bookings.get(i).getTime())) {
                // MAKE BOOKING

            } else if ((requestBooking.dentistid == bookings.get(i).getDentistid()) && (requestBooking.time == bookings.get(i).getTime())) {
                //  # dentists > # bookings of same dentist and same date&time .... MAKE BOOKING
                //check first how mangy bookings exist at the location at the desired time (loop) and then after that compare to # of dentists at the location
                long sameSlot = 0;
                for (int j = 0; j < bookings.size(); j++) {

                    if ((requestBooking.dentistid == bookings.get(j).getDentistid()) && (requestBooking.time == bookings.get(j).getTime())) {
                        sameSlot = sameSlot + 1;
                    }
                }
                long workingDentists = 0;
                for (int k = 0; k < dentists.size(); k++) {
                    if (requestBooking.dentistid == dentists.get(k).getId()) {
                        workingDentists = dentists.get(k).getDentistNumber();
                    }
                }

                if (workingDentists > sameSlot) {
                    //make booking
                }

            } else {
                //NOPE! send rejection
            }

        }
    }

    public Dentist makeDentist(MqttMessage message) throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object jsonObject = jsonParser.parse(message.toString());
        JSONObject parser = (JSONObject) jsonObject;

        long id = (Long) parser.get("id");
        long dentistNumber = (Long) parser.get("dentistNumber");

        // Creating a booking object using the fields from the parsed JSON
        Dentist newDentist = new Dentist(id, dentistNumber);
        return newDentist;
    }

    public Booking makeBooking(MqttMessage message) throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object jsonObject = jsonParser.parse(message.toString());
        JSONObject parser = (JSONObject) jsonObject;

        long userid = (Long) parser.get("userid");
        long requestid = (Long) parser.get("requestid");
        long dentistid = (Long) parser.get("dentistid");
        long issuance = (Long) parser.get("issuance");
        String time = (String) parser.get("time");

        // Creating a booking object using the fields from the parsed JSON
        Booking newBooking = new Booking(userid, requestid, dentistid, issuance, time);
        return newBooking;
    }


    public ReceivedBooking makeReceivedBooking(MqttMessage message) throws Exception {
        // Parsing message JSON
        JSONParser jsonParser = new JSONParser();
        Object jsonObject = jsonParser.parse(message.toString());
        JSONObject parser = (JSONObject) jsonObject;

        long userid = (Long) parser.get("userid");
        long requestid = (Long) parser.get("requestid");
        long dentistid = (Long) parser.get("dentistid");
        long issuance = (Long) parser.get("issuance");
        String time = (String) parser.get("time");

        // Creating a booking object using the fields from the parsed JSON
        ReceivedBooking newBooking = new ReceivedBooking(userid, requestid, dentistid, issuance, time);
        return newBooking;
    }
    // This method takes in the incoming MqttMessage and parses it, creating a new ConfirmedBooking object with filtered
    // fields
    /*public ConfirmedBooking makeConfirmedBooking(MqttMessage message) throws Exception {
        // Parsing message JSON
        JSONParser jsonParser = new JSONParser();
        Object jsonObject = jsonParser.parse(message.toString());
        JSONObject parser = (JSONObject) jsonObject;

        long userid = (Long) parser.get("userid");
        long requestid = (Long) parser.get("requestid");
        String time = (String) parser.get("time");

        // Creating a booking object using the fields from the parsed JSON
        ConfirmedBooking newBooking = new ConfirmedBooking(userid, requestid, time);
        return newBooking;
    }*/
}
