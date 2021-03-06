package com.example.danielsteenlosvik.nfc_example;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class MainActivity extends AppCompatActivity {

    TextView mTextView;
    EditText userInput;

    Boolean writeMode = false;

    // Nfcadapter
    NfcAdapter mAdapter;



    PendingIntent mPendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.text);
        userInput = (EditText) findViewById(R.id.message);
        userInput.setVisibility(View.INVISIBLE);

        mAdapter = NfcAdapter.getDefaultAdapter(this);


        /*

        // By giving a PendingIntent to another application,
        you are granting it the right to perform the operation
        you have specified as if the other application was yourself

        PendingIntent getActivity (
                Context context,
                int requestCode,
                Intent intent,
                int flags)
         */
        mPendingIntent = PendingIntent.getActivity(
                this,         // Context
                0,            // Requestcode
                new Intent(   // Intent
                        this,
                getClass()
                ).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),  // The activity will not be launched if it is already running at the top of the history stack.
                0);

        final Button writeButton = (Button) findViewById(R.id.write);
        writeButton.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                if(writeMode){
                    writeButton.setText("Change To Write Mode");
                    mTextView.setText("Waiting for NFC-tag to read...");
                    writeMode = false;
                    userInput.setVisibility(View.INVISIBLE);
                }
                else{
                    writeButton.setText("Change To Read Mode");
                    mTextView.setText("Input a message, than scan NFC-tag to write.");
                    writeMode = true;
                    userInput.setVisibility(View.VISIBLE);
                }
            }
        });
    }



    @Override
    protected void onResume() {
        super.onResume();
        // Gir denne aktivteten prioritet i behandling av NFC-tags

        mAdapter.enableForegroundDispatch(
                this,           // activity
                mPendingIntent, // PendingIntent
                null,           // IntentFilters [], null = allways dispatch
                null            // null på begge = acts a wildcard, will receive all tags via ACTION_TAG_DISCOVERED
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mAdapter != null) {

        // Fjerner foregrounddispatch ved pause
            mAdapter.disableForegroundDispatch(this);
        }
    }


    /**
     * Siden vi har satt FLAG_ACTIVITY_SINGLE_TOP kan vi hooke dette callbacket når NFC-tag scannes
     * @param intent
     */
    @Override
    protected void onNewIntent(Intent intent){
        Tag mytag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if(writeMode){
            try {
                write(userInput.getText().toString(), mytag);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else{
            read(intent);
        }
    }


    /*
     =================== READ ===================
     * @param intent
     */
    private void read(Intent intent){


        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES // Gir oss en array av NDEF-Message på tag
        );

        // Vi bryr oss bare om første NDEF-Message...
        NdefMessage msg = (NdefMessage) rawMsgs[0];

        // Vi sjekker kontrollbyte (første byte i payload)
        byte status = msg.getRecords()[0].getPayload()[0];


        // &-operasjonen vil gi sammenligne første byte i payload med 0x80 = 128
        // Hvis operasjonen gir 0 så betyr det at tegnsettet er utf8. Hvis ikke må
        // det være utf16 som bruker 16bit for hvert tegn
        int encoding = status & 0x80; // Bit mask 7th bit 1
         // 0x80 								= 1000 0000


         // Dette betyr at hvis 7. bit er satt så kan det ikke være UTF-8, siden disse bitsene
         // alltid er 0 i utf8
        String charset = null;
        if (encoding == 0)
            charset = "UTF-8";
        else
            charset = "UTF-16";

        int ianaLength = status & 0x3F; // Bit mask bit 5..0
        // 0x3f =                               = 0011 1111
        // F.eks status ==2                     = 0000 0010
        // Resultat av &-operasjon              = 0000 0010 = 2

        try {
            String content = new String(
                    msg.getRecords()[0].getPayload(),                           // byte[]
                    ianaLength + 1,                                             // offset
                    msg.getRecords()[0].getPayload().length - 1 - ianaLength,   // length = lengden til faktisk payload minus kontrollbyte og lengde på språkkode.
                    charset                                                     // charset
            );

            mTextView.setText(content);
        }
        catch(Throwable t) {
            t.printStackTrace();
        }
    }


     /*
     =================== WRITE ===================
     */

    private void write(String text, Tag tag) throws IOException, FormatException {

        // Lager et nytt record for å legge på NFC-tag
        NdefRecord[] records = { createRecord(text) };
        // Innkapsler alle NDEF-Records i NDEF-Message
        NdefMessage message = new NdefMessage(records);

        if(tag != null){

            Ndef ndef = Ndef.get(tag);
            ndef.connect();
            ndef.writeNdefMessage(message);
            ndef.close();
            mTextView.setText("Successfully wrote to tag");
        }
    }

    private NdefRecord createRecord(String text) throws UnsupportedEncodingException {


        String lang = "no";
        byte[] textBytes = text.getBytes();
        byte[] langBytes = lang.getBytes("US-ASCII");
        int langLength = langBytes.length;
        int textLength = textBytes.length;

        // Byte-array med payload. Allokkerer plass for språk, selve innhold, samt en kontrollbyte i starten
        byte[] payload = new byte[1 + langLength + textLength];

        // Kontrollbyte
        payload[0] = (byte) langLength;

        // Kopierer bytes for språk til payload
        System.arraycopy(langBytes, 0, payload, 1, langLength);

        // Kopierer bytes for beskjeden til payload
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

        // Vi har nå det vi trenger for å lage en NDEF-Record!
        NdefRecord recordNFC = new NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,      // indikerer at dette er en "velkjent" RTD (Record Type Definition)-type
                NdefRecord.RTD_TEXT,            // RTD Tekst er mappet til MIME-typen "text/plain".
                new byte[0],                    // Tom byte-array for id (vil ikke bli brukt)
                payload                         // Faktisk beskjed som legges inn i NDEF-Record
        );
        return recordNFC;
    }
}
