package io.github.kiritoxdone.hyperbackup.fixture;

import android.app.Activity;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Disposable fixture only. A normal launch reads values; seed is an explicit test action. */
public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Context de = createDeviceProtectedStorageContext();
        String seed = getIntent().getStringExtra("seed");
        try {
            if (seed != null) {
                getSharedPreferences("fixture", 0).edit().putString("value", seed).commit();
                de.getSharedPreferences("fixture", 0).edit().putString("value", seed + "-de").commit();
                SQLiteDatabase db = openOrCreateDatabase("fixture.db", 0, null);
                db.execSQL("CREATE TABLE IF NOT EXISTS values_table(value TEXT NOT NULL)");
                db.execSQL("DELETE FROM values_table");
                db.execSQL("INSERT INTO values_table VALUES(?)", new Object[]{seed});
                db.close();
                Files.write(new File(getFilesDir(), "value.txt").toPath(), seed.getBytes(StandardCharsets.UTF_8));
                Files.write(new File(getExternalFilesDir(null), "value.txt").toPath(), (seed + "-external").getBytes(StandardCharsets.UTF_8));
            }
            String value = getSharedPreferences("fixture", 0).getString("value", "missing");
            String device = de.getSharedPreferences("fixture", 0).getString("value", "missing");
            TextView text = new TextView(this);
            text.setTextSize(22); text.setPadding(32, 80, 32, 32);
            text.setText("Disposable HyperBackup fixture\nCE=" + value + "\nDE=" + device);
            setContentView(text);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
