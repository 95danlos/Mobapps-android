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

        mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

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
        mAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mAdapter != null) {
            mAdapter.disableForegroundDispatch(this);
        }
    }

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




    private void read(Intent intent){

        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) rawMsgs[0];

        byte status = msg.getRecords()[0].getPayload()[0];

        int enc = status & 0x80; // Bit mask 7th bit 1
        String encString = null;
        if (enc == 0)
            encString = "UTF-8";
        else
            encString = "UTF-16";

        int ianaLength = status & 0x3F; // Bit mask bit 5..0

        try {
            String content = new String(msg.getRecords()[0].getPayload(), ianaLength + 1, msg.getRecords()[0].getPayload().length - 1 - ianaLength, encString);
            mTextView.setText(content);
        }
        catch(Throwable t) {
            t.printStackTrace();
        }
    }

    private void write(String text, Tag tag) throws IOException, FormatException {

        NdefRecord[] records = { createRecord(text) };
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

        //create the message in according with the standard
        String lang = "en";
        byte[] textBytes = text.getBytes();
        byte[] langBytes = lang.getBytes("US-ASCII");
        int langLength = langBytes.length;
        int textLength = textBytes.length;

        byte[] payload = new byte[1 + langLength + textLength];
        payload[0] = (byte) langLength;

        // copy langbytes and textbytes into payload
        System.arraycopy(langBytes, 0, payload, 1, langLength);
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

        NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload);
        return recordNFC;
    }
}
