package com.timetracker.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import com.j256.ormlite.android.apptools.OrmLiteBaseActivity;
import com.timetracker.R;
import com.timetracker.domain.Task;
import com.timetracker.domain.TaskSwitchEvent;
import com.timetracker.domain.persistance.DatabaseHelper;

import java.sql.SQLException;
import java.util.*;

/**
 * Created by Anton Chernetskij
 */
public class ComparisonReportActivity extends OrmLiteBaseActivity<DatabaseHelper> {

    private static final int BAR_HEIGHT = 90;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.comparison_report);
        initBars();
    }

    private void initBars() {
        ListView tasksList = (ListView) findViewById(R.id.tasks);

        Date today = new Date();
        final List<AggregatedTaskItem> reportItems = generateReport(today, today);

        final long maxDuration = reportItems.get(0).duration;

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        final float width = size.x;

        tasksList.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return reportItems.size();
            }

            @Override
            public Object getItem(int position) {
                return reportItems.get(position);
            }

            @Override
            public long getItemId(int position) {
                return 0;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                Button view = new Button(ComparisonReportActivity.this);
                view.setGravity(Gravity.LEFT);
                view.setHeight(BAR_HEIGHT);
                view.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, BAR_HEIGHT));

                AggregatedTaskItem item = reportItems.get(position);
                view.setText(item.task.name + " " + buildTime(item.duration));
                BitmapDrawable drawable = getBackgroundBar((int) width, BAR_HEIGHT, (float)item.duration / maxDuration, item.task.color);
                view.setBackground(drawable);

                return view;
            }
        });
    }

    private BitmapDrawable getBackgroundBar(int width, int height, float portion, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint p = new Paint();
        p.setColor(color);
        p.setAlpha(Task.DEFAULT_COLOR_ALPHA);
        canvas.drawRect(0, 0, portion * width , height, p);
        return new BitmapDrawable(getResources(), bitmap);
    }

    private String buildTime(long time) {
        time /= 1000;
        long s = time % 60;
        time /= 60;
        long m = time % 60;
        time /= 60;
        long h = time % 60;
        return String.format("%d:%02d", h, m);
    }


    private class AggregatedTaskItem {
        public Task task;
        public long duration;

        private AggregatedTaskItem(Task task, long duration) {
            this.task = task;
            this.duration = duration;
        }
    }

    private List<AggregatedTaskItem> generateReport(Date from, Date to) {
        try {
            List<TaskSwitchEvent> events = getPreparedEvents(from, to);

            Map<Task, Long> durations = new HashMap<>();
            for (int i = 0; i < events.size(); i++) {
                TaskSwitchEvent event = events.get(i);
                Date endTime = i < (events.size() - 1) ? events.get(i + 1).switchTime : to;
                long duration = endTime.getTime() - event.switchTime.getTime();

                if (durations.containsKey(event.task)) {
                    Long t = durations.remove(event.task);
                    durations.put(event.task, t + duration);
                } else {
                    durations.put(event.task, duration);
                }
            }
            List<AggregatedTaskItem> result = new ArrayList<>(durations.size());
            for (Map.Entry<Task, Long> duration : durations.entrySet()) {
                result.add(new AggregatedTaskItem(duration.getKey(), duration.getValue()));
            }
            Collections.sort(result, new Comparator<AggregatedTaskItem>() {
                @Override
                public int compare(AggregatedTaskItem lhs, AggregatedTaskItem rhs) {
                    return -Long.valueOf(lhs.duration).compareTo(rhs.duration);
                }
            });
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<TaskSwitchEvent> getPreparedEvents(Date from, Date to) throws SQLException {
        List<TaskSwitchEvent> events = getHelper().getTaskEvents(from, to);

        TaskSwitchEvent firstEvent = null;
        Integer firstEventId = events.get(0).id;
        if (firstEventId > 1) {
            firstEvent = getHelper().getEventsDao().queryForId(firstEventId - 1);
        }

        if (firstEvent != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(from);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            firstEvent.switchTime = calendar.getTime();
            events.add(0, firstEvent);
        }
        return events;
    }
}
