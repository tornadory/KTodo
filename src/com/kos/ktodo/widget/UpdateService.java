package com.kos.ktodo.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.LinkedList;
import java.util.Queue;

public class UpdateService extends Service implements Runnable {
	public static final String ACTION_UPDATE_ALL = "com.kos.ktodo.widget.UPDATE_ALL";

	private static final String TAG = "UpdateService";
	private static final Object lock = new Object();
	private static final Queue<Integer> appWidgetIds = new LinkedList<Integer>();

	private static boolean threadRunning = false;

	public static void requestUpdate(final int[] appWidgetIds) {
		synchronized (lock) {
			for (final int widgetId : appWidgetIds)
				UpdateService.appWidgetIds.add(widgetId);
		}
	}

	private static boolean hasMoreUpdates() {
		synchronized (lock) {
			return !appWidgetIds.isEmpty();
		}
	}

	private static int getNextUpdate() {
		synchronized (lock) {
			if (appWidgetIds.peek() == null) {
				return AppWidgetManager.INVALID_APPWIDGET_ID;
			} else {
				return appWidgetIds.poll();
			}
		}
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId) {
		// If requested, trigger update of all widgets
		if (ACTION_UPDATE_ALL.equals(intent.getAction())) {
			requestUpdateAll(this);
		}

		// Only start processing thread if not already running
		synchronized (lock) {
			if (!threadRunning) {
				threadRunning = true;
				new Thread(this).start();
			}
		}

		return super.onStartCommand(intent, flags, startId);
	}

	public static void requestUpdateAll(final Context ctx) {
		final AppWidgetManager manager = AppWidgetManager.getInstance(ctx);
		requestUpdate(manager.getAppWidgetIds(new ComponentName(ctx, KTodoWidget.class)));
	}

	public void run() {
		final WidgetSettingsStorage settingsStorage = new WidgetSettingsStorage(this);
		while (true) {
			settingsStorage.open();
			final AppWidgetManager widgetManager = AppWidgetManager.getInstance(this);
			while (hasMoreUpdates()) {
				final int widgetId = getNextUpdate();
				Log.i(TAG, "updating widget " + widgetId);
				final WidgetSettings s = settingsStorage.load(widgetId);
				if (!s.configured) continue;
				final RemoteViews updViews = KTodoWidget.buildUpdate(this, widgetId);
				if (updViews != null)
					widgetManager.updateAppWidget(widgetId, updViews);
			}
			settingsStorage.close();

			//schedule next update at noon
			final Time t = new Time();
			t.set(System.currentTimeMillis() + DateUtils.DAY_IN_MILLIS);
			t.hour = 0;
			t.minute = 0;
			t.second = 0;

			final Intent intent = new Intent(ACTION_UPDATE_ALL);
			intent.setClass(this, this.getClass());
			final PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			final AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			alarmMgr.set(AlarmManager.RTC_WAKEUP, t.toMillis(false), pendingIntent);
			synchronized (lock) {
				if (!hasMoreUpdates()) {
					threadRunning = false;
					stopSelf();
					break;
				}
			}
		}
	}

	@Override
	public IBinder onBind(final Intent intent) {
		return null;
	}
}