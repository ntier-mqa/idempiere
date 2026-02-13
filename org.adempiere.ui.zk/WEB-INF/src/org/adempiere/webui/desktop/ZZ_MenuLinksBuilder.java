package org.adempiere.webui.desktop;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

import org.adempiere.webui.component.ToolBarButton;
import org.compiere.model.I_AD_Menu;
import org.compiere.model.MAttachment;
import org.compiere.model.MAttachmentEntry;
import org.compiere.model.MDashboardContent;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.A;
import org.zkoss.zul.Div;
import org.zkoss.zul.Filedownload;
import org.zkoss.zul.Label;
import org.zkoss.zul.Style;
import org.zkoss.zul.Vlayout;

import za.ntier.models.X_ZZ_Funding_Policy;

public final class ZZ_MenuLinksBuilder {

	private static final CLogger log = CLogger.getCLogger(ZZ_MenuLinksBuilder.class);

	private ZZ_MenuLinksBuilder() {}

	/**
	 * Creates and adds the header panel (year/title/date range) and the fixed,
	 * scrollable menu to the provided components list. Minimal controller code.
	 */

	public static void attachHeaderAndMenu(List<Component> components,
			MDashboardContent dashboardContent,
			EventListener<Event> clickListener) {

		// 1) Header first
		Div header = buildHeaderPanel();

		// ⬇⬇⬇ CHANGE THIS ⬇⬇⬇
		// components.add(header);           // old – appends at bottom
		components.add(0, header);          // new – put header before iframe
		// ⬆⬆⬆

		// 2) Menu list
		Vlayout list = fromQuery(dashboardContent, clickListener, "0");
		list.setSclass("menu-links");
		list.setStyle("overflow-y:auto;margin:0;");

		Div fixed = new Div();
		fixed.setSclass("zz-fixedmenu");
		fixed.appendChild(list);
		fixed.setId("zzFixedMenu");


		Style css = new Style();


		css.setContent(
				".zz-headerwrap{ left:35px!important; padding-left:0!important; }" +
				// 🔹 existing rules
				".zz-fixedmenu{position:fixed; top:var(--menuTop, 280px); left:60px !important; z-index:2000;}" +
				".zz-fixedmenu .menu-links{max-height:calc(100vh - var(--menuTop, 280px) - 12px); overflow-y:auto;}" +

		    ".dashboard-widget .z-panelchildren{" +
		    "overflow:hidden !important;" +
		    "padding:0 !important;" +
		    "margin:0 !important;" +
		    "min-height:100vh;" +
		    "background-image:url('https://i.ibb.co/bR22DSsS/Whats-App-Image-2025-10-01-at-12-21-57.jpg');" +
		    "background-repeat:no-repeat;" +
		    "background-size:cover;" +
		    "background-position:center top;" +
		    "}" +
		    ".dashboard-widget.dashboard-widget-max .z-panelchildren{" +
		    "overflow:hidden !important;" +
		    "padding:0 !important;" +
		    "margin:0 !important;" +
		    "min-height:100vh;" +
		    "background-image:url('https://i.ibb.co/bR22DSsS/Whats-App-Image-2025-10-01-at-12-21-57.jpg');" +
		    "background-repeat:no-repeat;" +
		    "background-size:cover;" +
		    "background-position:center top;" +
		    "}" +
		    ".zz-headerwrap{" +
		    "position:absolute;" +
		    "top:32px;" +
		    "left:48px;" +
		    "z-index:2100;" +
		    "background:transparent;" +
		    "}" +
		    ".dashboard-widget .z-panel-header{display:none!important;}" +
		    "/* Remove header bar + underline for dashboard widgets on Home tab */" +
		    ".desktop-home-tabpanel .dashboard-widget > .z-panel-head," +
		    ".desktop-home-tabpanel .dashboard-widget > .z-panel-head > .z-panel-header{" +
		    "  display:none!important;" +
		    "  height:0!important;" +
		    "  min-height:0!important;" +
		    "  padding:0!important;" +
		    "  margin:0!important;" +
		    "  border:0!important;" +
		    "}"  +
		    ".desktop-home-tabpanel .dashboard-widget {" +
		    "  margin-top: -37px !important;" +
		    "}"  




				);
		fixed.appendChild(css);





		// only once:
		components.add(fixed);

		installMenuTopAutoCalc(header);

		// policy footer…
		Div policyFooter = new Div();
		policyFooter.setId("zzPolicyFooter");
		policyFooter.setStyle(
				"position:fixed; right:72px; bottom:24px; z-index:2000;" +
						"display:flex; align-items:center; gap:8px;"
				);
		A fundingLink = new A("Funding Policy");
		fundingLink.setStyle("text-decoration: underline; cursor: pointer; font-weight:600; color: #2d2c72;");
		fundingLink.addEventListener(Events.ON_CLICK, ev -> downloadFundingPolicy());
		policyFooter.appendChild(fundingLink);
		components.add(policyFooter);
	}











	// ============================================================
	// HEADER BUILDER (year/title/dates)
	// ============================================================
	private static Div buildHeaderPanel() {
		HeaderData data = fetchHeaderData();

		Div wrapper = new Div();
		wrapper.setId("zzHeaderPanel");  
		wrapper.setSclass("zz-headerwrap");  
		
		wrapper.setStyle(
			    "background: transparent;" +
			    "padding:24px 0;" +      // remove extra left padding
			    "border-radius:16px;" +
			    "margin:0 0 16px 0;" +
			    "box-shadow:none;" +
			    "color:#fff;"
			);

		Vlayout v = new Vlayout();
		v.setSpacing("2px");
		wrapper.appendChild(v);

		// Line 1: Year (C_Year.description)
		Label lblYear = new Label(data.yearText == null || data.yearText.isBlank() ? "—" : data.yearText.trim());
		//lblYear.setStyle("display:block;font-size:48px;font-weight:800;letter-spacing:1px;line-height:1.0;margin:0 0 6px 0;");  Not so white
		lblYear.setStyle(
				"display:block;font-size:48px;font-weight:800;letter-spacing:1px;line-height:1.0;margin:0 0 6px 0;"
						+ "color:rgba(255,255,255,0.96);"
						+ "text-shadow:0 1px 2px rgba(0,0,0,.55);"
				);
		v.appendChild(lblYear);

		// Line 2: Orange title (ZZ_Menu_Title)
		String title = (data.menuTitle == null || data.menuTitle.isBlank())
				? "DISCRETIONARY GRANT APPLICATIONS" : data.menuTitle.trim();
		Label lblTitle = new Label(title);
		lblTitle.setStyle("display:block;font-size:36px;font-weight:900;text-transform:uppercase;line-height:1.0;color:#ff6a00;margin:0 0 8px 0;");
		v.appendChild(lblTitle);

				
		// Line 3: "1st Window | dd MMMM yyyy HH:mm - dd MMMM yyyy HH:mm"
		
		String windowLine = data.window + " Window | —";
		if (data.startDT != null && data.endDT != null) {
		    DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd MMMM uuuu");
		    DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

		    windowLine =
		        data.window + " Window | "
		        + dateFmt.format(data.startDT) + " Time " + timeFmt.format(data.startDT)
		        + " - "
		        + dateFmt.format(data.endDT)   + " Time " + timeFmt.format(data.endDT);
		}
		Label lblWindow = new Label(windowLine);
		
		lblWindow.setStyle(
			    "display:block;font-size:18px;font-weight:600;letter-spacing:0.3px;margin:0;"
			  + "color:rgba(255,255,255,0.92);"
			  + "text-shadow:0 1px 2px rgba(0,0,0,.5);"
			);
		v.appendChild(lblWindow);

		// Line 4: Remaining time (based on end timestamp vs now)
		Label lblRemaining = new Label(buildRemainingText(data.endDT));
		lblRemaining.setStyle(
		    "display:block;font-size:16px;font-weight:600;letter-spacing:0.2px;margin:6px 0 0 0;"
		  + "color:rgba(255,255,255,0.90);"
		  + "text-shadow:0 1px 2px rgba(0,0,0,.5);"
		);
		v.appendChild(lblRemaining);

		return wrapper;
	}

	
	
	private static String buildRemainingText(LocalDateTime endDT) {
	    if (endDT == null) return "—";

	    ZoneId zone = ZoneId.systemDefault();
	    ZonedDateTime now = ZonedDateTime.now(zone);
	    ZonedDateTime end = endDT.atZone(zone);

	    Duration d = Duration.between(now, end);

	    if (d.isZero() || d.isNegative()) {
	        return "Closed";
	    }

	    long totalMinutes = d.toMinutes();

	    long days = totalMinutes / (24 * 60);
	    long rem = totalMinutes % (24 * 60);

	    long hours = rem / 60;
	    long minutes = rem % 60;

	    // Example: "15 days 7 hours 16 minutes"
	    String timePart =  days + " days " + hours + " hours " + minutes + " minutes";
	    return timePart + " remaining before close";
	}
	
	private static class HeaderData {
	    String window;  // 1st ,2nd , 3rd ...
	    String yearText;
	    String menuTitle;

	    LocalDateTime startDT;
	    LocalDateTime endDT;
	}

	
	private static HeaderData fetchHeaderData() {
	    HeaderData h = new HeaderData();

	    final String sql =
	        "SELECT y.description AS year_desc, " +
	        "       oa.zz_menu_title, " +
	        "       oa.startdate     AS start_ts, " +
	        "       oa.enddate       AS end_ts, " +
	        "       oa.zz_window     AS zz_window " +
	        "FROM adempiere.zz_open_application oa " +
	        "JOIN adempiere.c_year y ON y.c_year_id = oa.c_year_id " +
	        "WHERE oa.isactive = 'Y' " +
	        "  AND oa.zz_docstatus = 'AP' " +
	        "  AND now() BETWEEN oa.startdate AND oa.enddate " +
	        "ORDER BY oa.startdate DESC " +
	        "LIMIT 1";

	    try (PreparedStatement ps = DB.prepareStatement(sql, null);
	         ResultSet rs = ps.executeQuery()) {

	        if (rs.next()) {
	            h.yearText  = rs.getString("year_desc");
	            h.menuTitle = rs.getString("zz_menu_title");
	            h.window    = rs.getString("zz_window");

	            java.sql.Timestamp st = rs.getTimestamp("start_ts");
	            java.sql.Timestamp et = rs.getTimestamp("end_ts");

	            if (st != null) h.startDT = st.toLocalDateTime();
	            if (et != null) h.endDT   = et.toLocalDateTime();
	        }
	    } catch (Exception e) {
	        log.warning("Failed to load header data: " + e.getMessage());
	    }
	    return h;
	}

	// ============================================================
	// EXISTING PUBLIC BUILDERS (unchanged behavior)
	// ============================================================
	/** Build a vertical list from explicit menu IDs */
	public static Vlayout fromIds(
			MDashboardContent dashboardContent,
			Collection<Integer> menuIds,
			EventListener<Event> clickListener,
			String marginCss // e.g. "260px 0 0 0"
			) {
		Vlayout list = baseList(marginCss);
		for (int id : menuIds) {
			I_AD_Menu menu = dashboardContent.getAD_Menu(id);
			if (menu == null) continue;
			list.appendChild(makeButton(id, menu.getName(), clickListener));
		}
		return list;
	}

	/** Build a vertical list from a DB query (adjust WHERE as needed) */
	public static Vlayout fromQuery(
			MDashboardContent dashboardContent,
			EventListener<Event> clickListener,
			String marginCss
			) {
		Vlayout list = baseList(marginCss);

		String sql =
				"WITH open_apps AS ( "
						+ "    SELECT DISTINCT oa.ad_org_id, oa.documentno, TRIM(both ' ' FROM x)::NUMERIC AS zz_program_master_data_id "
						+ "    FROM adempiere.zz_open_application oa "
						+ "    CROSS JOIN LATERAL unnest(string_to_array(oa.zz_programs, ',')) AS t(x) "
						+ "    WHERE oa.isactive = 'Y' "
						+ "      AND now() BETWEEN oa.startdate AND oa.enddate "
						+ "      AND oa.zz_docstatus = 'AP' "
						+ "      AND oa.zz_programs IS NOT NULL "
						+ "), "
						+ "program_uu AS ( "
						+ "    SELECT p.zz_program_master_data_uu, a.ad_org_id, a.documentno "
						+ "    FROM adempiere.zz_program_master_data p "
						+ "    JOIN open_apps a ON a.zz_program_master_data_id = p.zz_program_master_data_id "
						+ "    WHERE p.isactive = 'Y' "
						+ "      AND p.zz_program_master_data_uu IS NOT NULL "
						+ "), "
						+ "menus AS ( "
						+ "    SELECT m.ad_menu_id, m.name, m.predefinedcontextvariables "
						+ "    FROM adempiere.ad_menu m "
						+ "    JOIN adempiere.ad_form f ON f.ad_form_id = m.ad_form_id "
						+ "    WHERE m.isactive = 'Y' "
						+ "      AND m.issummary = 'N' "
						+ "      AND f.ad_form_id = 1000000 "
						+ ") "
						+ "SELECT ad_menu_id "
						+ "FROM ( "
						+ "    SELECT DISTINCT ON (m.ad_menu_id) "
						+ "           m.ad_menu_id, "
						+ "           m.name, "
						+ "           CASE WHEN m.ad_menu_id = 1000072 THEN 0 ELSE 1 END AS sort_top, "
						+ "           ao.name AS org_name, "
						+ "           u.documentno AS docno "
						+ "    FROM menus m "
						+ "    LEFT JOIN program_uu u "
						+ "      ON m.predefinedcontextvariables ILIKE ('%' || 'ZZ_Program_Master_Data_UU=' || u.zz_program_master_data_uu || '%') "
						+ "    LEFT JOIN adempiere.ad_org ao ON ao.ad_org_id = u.ad_org_id "
						+ "    WHERE u.zz_program_master_data_uu IS NOT NULL "
						+ "       OR m.ad_menu_id = 1000072 "
						+ "    ORDER BY m.ad_menu_id, ao.name, u.documentno DESC NULLS LAST "
						+ ") s "
						+ "ORDER BY sort_top, "
						+ "         name ASC, "
						+ "         org_name ASC, "
						+ "         docno DESC NULLS LAST, "
						+ "         ad_menu_id";

		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = DB.prepareStatement(sql, null);
			rs = ps.executeQuery();
			while (rs.next()) {
				int id = rs.getInt(1);
				I_AD_Menu menu = dashboardContent.getAD_Menu(id);
				if (menu == null) continue;
				list.appendChild(makeButton(id, menu.getName(), clickListener));
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			DB.close(rs, ps);
		}

		return list;
	}

	private static Vlayout baseList(String marginCss) {
		Vlayout list = new Vlayout();
		list.setSpacing("2px");
		list.setSclass("menu-links");
		list.setStyle("margin:" + (marginCss == null ? "0" : marginCss) + ";align-items:flex-start;");

		// CSS: prevent wrappers/buttons from stretching full width
		Style st = new Style();
		st.setContent(
				".menu-links .z-vlayout-inner{width:auto !important;}"
						+ ".menu-links .z-toolbarbutton{width:auto !important;display:inline-block;}"
						+ ".menu-links .z-toolbarbutton .z-toolbarbutton-content{justify-content:flex-start!important;}"
				);
		list.appendChild(st);

		// Run the width-equalizing JS only on the UI thread, after attachment.
		list.addEventListener(Events.ON_CREATE, ev -> runFixMenuLinkWidths((Vlayout) ev.getTarget()));
		list.addEventListener(Events.ON_AFTER_SIZE, ev -> runFixMenuLinkWidths((Vlayout) ev.getTarget()));

		return list;
	}

	/** UI-thread-only: measure max width and set all buttons to that width */
	private static void runFixMenuLinkWidths(Vlayout list) {
		// If not on a UI execution yet, try scheduling on the Desktop
		if (Executions.getCurrent() == null) {
			if (list.getDesktop() != null) {
				Executions.schedule(list.getDesktop(), e -> runFixMenuLinkWidths(list),
						new org.zkoss.zk.ui.event.Event("onFixMenuLinkWidths", list));
			}
			return;
		}

		final String uuid = list.getUuid();
		final String js =
				"(function(){\n"
						+ "  var w = zk.Widget.$('$" + uuid + "'); if(!w) return;\n"
						+ "  var n = w.$n(); if(!n) return;\n"
						+ "  var btns = n.querySelectorAll('.z-toolbarbutton'); if(!btns.length) return;\n"
						+ "  var max = 0;\n"
						+ "  btns.forEach(function(b){ b.style.width=''; var rw=b.getBoundingClientRect().width; if(rw>max) max=rw;});\n"
						+ "  btns.forEach(function(b){ b.style.width = max + 'px'; });\n"
						+ "})();";

		Clients.evalJavaScript(js);
	}

	private static ToolBarButton makeButton(int id, String label, EventListener<Event> clickListener) {
		ToolBarButton btn = new ToolBarButton(String.valueOf(id));
		btn.setLabel(label);
		btn.setAttribute("AD_Menu_ID", id);
		btn.addEventListener(Events.ON_CLICK, clickListener);

		// default style
		String style =
				"display:inline-block;"
						+ "margin:0;"
						+ "padding:2px 8px;"
						+ "text-align:left !important;"
						+ "line-height:1.1;"
						+ "font-size:18px !important;"
						+ "color:#fff !important;";

		// If it's "My Applications", override to orange + bold
		if ("My Applications".equalsIgnoreCase(label)) {
			style += "color:#F27127 !important;font-weight:800;";
		}

		btn.setStyle(style);
		return btn;
	}

	private static void installMenuTopAutoCalc(org.zkoss.zul.Div hook) {
		final String INIT_JS =
				"(function(){"
						+ "  function alignMenu(){"
						+ "    var header = document.getElementById('zzHeaderPanel');"
						+ "    var fixed  = document.querySelector('.zz-fixedmenu');"
						+ "    if(!fixed) return;"
						+ "    var top = 280;"
						+ "    var desiredLeft = 0;"
						+ "    if(header){"
						+ "      var hr = header.getBoundingClientRect();"
						+ "      top = hr.bottom + 12;"
						+ "      // header text left: first label inside the header"
						+ "      var firstHeaderLabel = header.querySelector('.z-label');"
						+ "      var headerTextLeft = firstHeaderLabel ? firstHeaderLabel.getBoundingClientRect().left : hr.left;"
						+ "      // menu text left: first menu button inner content"
						+ "      var firstBtnContent = fixed.querySelector('.z-toolbarbutton .z-toolbarbutton-content');"
						+ "      var innerOffset = 0;"
						+ "      if(firstBtnContent){"
						+ "        // measure how far the inner text is from the fixed wrapper's left"
						+ "        var fr = fixed.getBoundingClientRect();"
						+ "        var br = firstBtnContent.getBoundingClientRect();"
						+ "        innerOffset = br.left - fr.left;"
						+ "      }"
						+ "      // optional small gutter to move slightly more right (adjust as needed)"
						+ "      var tweak = 6;  /* try 6–12 if you want more */"
						+ "      desiredLeft = Math.max(0, Math.round(headerTextLeft - innerOffset + tweak));"
						+ "    }"
						+ "    fixed.style.setProperty('--menuTop', top + 'px');"
						+ "    fixed.style.left = desiredLeft + 'px';"
						+ "  }"
						+ "  if(!window.__zzMenuAlignInstalled){"
						+ "    window.__zzMenuAlignInstalled = true;"
						+ "    window.addEventListener('resize', alignMenu, {passive:true});"
						+ "    window.addEventListener('scroll', alignMenu, {passive:true});"
						+ "  }"
						+ "  alignMenu();"
						+ "})();";

		hook.addEventListener(org.zkoss.zk.ui.event.Events.ON_CREATE,
				ev -> org.zkoss.zk.ui.util.Clients.evalJavaScript(INIT_JS));

		hook.addEventListener(org.zkoss.zk.ui.event.Events.ON_AFTER_SIZE,
				ev -> org.zkoss.zk.ui.util.Clients.evalJavaScript(
						"(function(){ if(window.__zzMenuAlignInstalled){ var e=new Event('resize'); window.dispatchEvent(e);} })();"
						));

		if (org.zkoss.zk.ui.Executions.getCurrent() == null && hook.getDesktop() != null) {
			org.zkoss.zk.ui.Executions.schedule(
					hook.getDesktop(),
					e -> org.zkoss.zk.ui.util.Clients.evalJavaScript(INIT_JS),
					new org.zkoss.zk.ui.event.Event("onCreate", hook)
					);
		}
	}


	private static void downloadFundingPolicy() {

		// 1) Find the active policy record by date range
		int policyId = getCurrentFundingPolicyId();
		if (policyId <= 0) {
			Clients.showNotification(
					"No active Funding Policy is defined for the current date.",
					"warning",
					null,
					"top_center",
					3500
					);
			return;
		}

		// 2) Load the PO (mostly so we have the proper table context)
		X_ZZ_Funding_Policy policy =
				new X_ZZ_Funding_Policy(Env.getCtx(), policyId, null);

		// 3) Load the attachment for this record
		MAttachment attachment =
				MAttachment.get(Env.getCtx(), policy.get_Table_ID(), policy.get_ID());

		if (attachment == null || attachment.getEntryCount() == 0) {
			Clients.showNotification(
					"The current Funding Policy has no document attached.",
					"warning",
					null,
					"top_center",
					3500
					);
			return;
		}

		// 4) Decide which attachment entry to use
		//    (here: most recent / last one)
		int entryIndex = attachment.getEntryCount() - 1;
		MAttachmentEntry entry = attachment.getEntry(entryIndex);

		if (entry == null || entry.getData() == null) {
			Clients.showNotification(
					"The Funding Policy attachment is empty.",
					"warning",
					null,
					"top_center",
					3500
					);
			return;
		}

		byte[] data = entry.getData();
		String fileName = entry.getName();

		if (fileName == null || fileName.isBlank()) {
			fileName = "FundingPolicy.pdf";
		}

		// Extract extension
		String ext = "pdf";
		int dot = fileName.lastIndexOf('.');
		if (dot >= 0 && dot < fileName.length() - 1) {
			ext = fileName.substring(dot + 1).toLowerCase();
		}

		// Very simple content-type mapping
		String contentType;
		switch (ext) {
		case "pdf":
			contentType = "application/pdf";
			break;
		case "doc":
			contentType = "application/msword";
			break;
		case "docx":
			contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			break;
		default:
			contentType = "application/octet-stream";
			break;
		}

		// 5) Serve via ZK
		AMedia media = new AMedia(fileName, ext, contentType, data);
		Filedownload.save(media);
	}


	/**
	 * Returns the ZZ_Funding_Policy_ID for the most recent policy
	 * whose date range contains now(). Returns 0 if none.
	 */
	private static int getCurrentFundingPolicyId() {
		final String sql =
				"SELECT ZZ_Funding_Policy_ID " +
						"FROM adempiere.zz_funding_policy " +
						"WHERE IsActive = 'Y' " +
						"  AND now() BETWEEN StartDate AND EndDate "
						+ " AND ZZ_DocStatus = 'CO' " +
						" ORDER BY StartDate DESC, EndDate DESC, ZZ_Funding_Policy_ID DESC " +
						"LIMIT 1";

		return DB.getSQLValue(null, sql);
	}




}


