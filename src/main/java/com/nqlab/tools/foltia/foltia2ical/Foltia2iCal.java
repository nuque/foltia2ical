package com.nqlab.tools.foltia.foltia2ical;

import static spark.Spark.*;
import static spark.SparkBase.*;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Method;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.util.UidGenerator;

public class Foltia2iCal {

	public static void main(String[] args) {

		port(7654);

		// 録画対象の番組を検索し、icalに変換する。
		get("/foltia2ical",
				(req, res) -> {

					Calendar calendar = new Calendar();
					calendar.getProperties().add(
							new ProdId("-//nqlab.com//foltia2ical//JP"));
					calendar.getProperties().add(CalScale.GREGORIAN);
					calendar.getProperties().add(Version.VERSION_2_0);
					calendar.getProperties().add(Method.PUBLISH);
					calendar.getProperties().add(
							new XProperty("X-WR-CALNAME", "foltia2ical"));

					SimpleDateFormat dtf = new SimpleDateFormat("yyyyMMddHHmm");

					List<Map<String, String>> programs = getAllRecordPrograms();
					UidGenerator ug = new UidGenerator("1");
					for (Map<String, String> program : programs) {
						VEvent event = new VEvent();
						// UID
						event.getProperties().add(ug.generateUid());
						// サマリ
						event.getProperties().add(
								new Summary(program.get("title") + "-"
										+ program.get("countno") + "-"
										+ program.get("subtitle")));
						// 詳細
						event.getProperties().add(
								new Description("録画ステータス : "
										+ program.get("filestatus")));
						// 最終更新日時
						if (program.containsKey("lastupdate")) {
							event.getProperties().add(
									new LastModified(new DateTime(Long
											.parseLong(program
													.get("lastupdate")))));
						}
						// 開始日時
						event.getProperties().add(
								new DtStart(new DateTime(dtf.parse(program
										.get("startdatetime")))));
						// 終了日時
						event.getProperties().add(
								new DtEnd(new DateTime(dtf.parse(program
										.get("enddatetime")))));
						event.getProperties().add(new Organizer("foltia2ical"));

						calendar.getComponents().add(event);
					}

					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					CalendarOutputter outputter = new CalendarOutputter();
					outputter.output(calendar, baos);
					return baos.toString("UTF-8");
				});
	}

	/**
	 * 録画対象の番組リスト取得
	 */
	public static List<Map<String, String>> getAllRecordPrograms() {
		// データ格納
		List<Map<String, String>> programValueMapList = new ArrayList<>();

		try (Connection conn = DriverManager.getConnection(
				"jdbc:postgresql://localhost/foltia", "foltia", "foltia");
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("select " + //
						"  pg.tid, " + //
						"  pg.title, " + //
						"  st.subtitle, " + //
						"  st.countno, " + //
						"  st.startdatetime, " + //
						"  st.enddatetime, " + //
						"  st.lastupdate, " + //
						"  st.filestatus " + //
						"from " + //
						"  foltia_subtitle st " + //
						"  inner join foltia_program pg on pg.tid=st.tid " + //
						"  inner join foltia_tvrecord tr " + //
						"    on pg.tid=tr.tid " + //
						"    and st.stationid=tr.stationid " //
				);) {
			while (rs.next()) {
				Map<String, String> pgValueMap = new HashMap<>();
				pgValueMap.put("tid", Long.toString(rs.getLong("tid")));
				pgValueMap.put("title", rs.getString("title"));
				pgValueMap.put("subtitle", rs.getString("subtitle"));
				pgValueMap.put("countno",
						Integer.toString(rs.getInt("countno")));
				pgValueMap.put("startdatetime",
						Long.toString(rs.getLong("startdatetime")));
				pgValueMap.put("enddatetime",
						Long.toString(rs.getLong("enddatetime")));
				java.util.Date lastupdate = rs.getTimestamp("lastupdate");
				if (lastupdate != null) {
					pgValueMap.put("lastupdate",
							Long.toString(lastupdate.getTime()));
				}
				pgValueMap.put("filestatus",
						Integer.toString(rs.getInt("filestatus")));
				programValueMapList.add(pgValueMap);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return programValueMapList;
	}
}
