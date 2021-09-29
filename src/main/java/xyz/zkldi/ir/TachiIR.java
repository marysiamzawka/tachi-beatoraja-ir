package bms.player.beatoraja.ir;

import java.util.Map;

import javax.imageio.spi.ImageReaderSpi;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;

import bms.player.beatoraja.MainController;

import bms.player.beatoraja.PlayerInformation;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.song.SongInformation;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.play.JudgeProperty;
import bms.player.beatoraja.ir.*;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.MainController;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;

// thank you to Seraphin- for this code.
// I have modified it for my own ends, but the same base is here.

public class TachiIR implements IRConnection {
	public static final String NAME = System.getenv("TCHIR_NAME");
	public static final String HOME = System.getenv("TCHIR_HOME");
	public static final String VERSION = "2.0.0";
	public static final String BEATORAJA_CLIENT_VERSION = MainController.getVersion();

	private static final String BASE_URL = System.getenv("TCHIR_BASE_URL");

	private String apiToken = "";

	class ResponseCreator<T> {
		public IRResponse<T> create(final boolean success, final String msg, final T data) {
			return new IRResponse<T>() {
				public boolean isSucceeded() { return success; }
				public String getMessage() { return msg; }
				public T getData() { return data; }
			};
		}
	}
	
	class TachiResponse {
		boolean success;
		String description;
		JsonNode body;
		
		TachiResponse(JsonNode actualObj) {
			success = actualObj.get("success").asBoolean();
			description = actualObj.get("description").asText();
			body = actualObj.get("body");
		}
	}

	/**
	 * Makes a GET request to BASE_URL + url.
	 */
	TachiResponse GETRequest(String url) throws Exception {
		OkHttpClient client = new OkHttpClient();

		Request request = new Request.Builder()
			.url(BASE_URL + url)
			.header("User-Agent", "OKHTTP")
			.header("X-TachiIR-Version", VERSION)
			.addHeader("Authorization", "Bearer " + apiToken)
			.addHeader("Accept", "application/json")
			.build();

		try (Response response = client.newCall(request).execute()) {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode actualObj = mapper.readTree(response.body().string());

			return new TachiResponse(actualObj);
		}
	}

	/**
	 * Makes a POST request to BASE_URL + url. Sends the 2nd argument
	 * as the request body.
	 */
	TachiResponse POSTRequest(String url, String JSON) throws Exception {
		OkHttpClient client = new OkHttpClient();
		// charset=utf-8 is redundant, but is here just incase.
		RequestBody body = RequestBody.create(MediaType.get("application/json; charset=utf-8"), JSON);

		Request request = new Request.Builder()
			.url(BASE_URL + url)
			.header("User-Agent", "OKHTTP")
			.header("X-TachiIR-Version", VERSION)
			.addHeader("Accept", "application/json")
			.addHeader("Authorization", "Bearer " + apiToken)
			.post(body)
			.build();

		try (Response response = client.newCall(request).execute()) {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode actualObj = mapper.readTree(response.body().string());

			return new TachiResponse(actualObj);
		}
	}

	/**
	 * Utility wrapper for logging to stdout.
	 */
	private void log(String message) {
		System.out.println("[" + NAME + " IR] (" + VERSION + ") " + message);
	}

	/**
	 * Utility wrapper for throwing a generic exception.
	 */
	private void _throw() throws Exception {
		throw new Exception("An internal error has occured.");
	}

	/**
	 * Since we extend/implement a class with the IR, we're not allowed
	 * to use the "throws exception" function signature modifier.
	 * 
	 * This is the only way to throw errors, and is generally a horrific idea.
	 * Ah well.
	 */
	private void panic() {
		throw new RuntimeException("This build of TachiIR is critically broken. Report this, or check the logs above to see if it was your fault.");
	}
	private void panic(String message) {
		throw new RuntimeException(message);
	}

	public IRResponse<IRPlayerData> register(String id, String pass, String name) { return null; }

	/**
	 * Basically does nothing. Performs some init and status checks for the
	 * IR.
	 * 
	 * Authentication is already handled with API keys, and users are
	 * expected to place their relevant API key inside `password`.
	 */
	public IRResponse<IRPlayerData> login(String id, String pass) {
		if (BASE_URL == "") {
			log("No BASE_URL. This build of TachiIR is likely to be broken. Report this.");
			panic();
		}

		if (!BEATORAJA_CLIENT_VERSION.startsWith("lr2oraja")) {
			log("Invalid client. " + NAME + " IR only supports lr2oraja.");
			panic("Invalid client. " + NAME + " IR only supports lr2oraja.");
		}

		ResponseCreator<IRPlayerData> rc = new ResponseCreator<IRPlayerData>();

		// We grab the apiToken from the users password. Their username doesn't actually matter.
		// This is for separation of authentication concerns.
		apiToken = pass;

		try {
			TachiResponse resp = GETRequest("/api/v1/status");
	
			if (resp.success) {
				log("Connected to " + BASE_URL + ".");

				TachiResponse userResp = GETRequest("/api/v1/users/" + resp.body.get("whoami").asText());

				if (userResp.success) {
					log("Authenticated as " + userResp.body.get("username").asText() + ".");
				}
				else {
					log("Failed to find out who you are. That's not good!");
					_throw();
				}
			}
			else {
				log("An error has occured in logging in. Please check your details.");
				_throw();
			}
	
			return rc.create(resp.success, resp.description, null);
		}
		catch (Exception e) {
			System.out.println(e.toString());
			return rc.create(false, "Internal Exception", null);
		}
	}

	class PlayData {
		public IRChartData chart;
		public IRScoreData score;
		public String client;

		PlayData(IRChartData model, IRScoreData scoreData) {
			chart = model;
			score = scoreData;
			client = MainController.getVersion();
		}
	}

	/**
	 * Submits a score to the IR. This POSTs data out to submit-score.
	 * 
	 * @warn This basically just serialises IRScoreData. If a beatoraja
	 * update causes this to collapse in on itself, that sucks.
	 */
	public IRResponse<Object> sendPlayData(IRChartData model, IRScoreData score) {
		ResponseCreator<Object> rc = new ResponseCreator<Object>();

		PlayData playData = new PlayData(model, score);

		try {
			ObjectWriter ow = new ObjectMapper().writer();
			String json = ow.writeValueAsString(playData);
	
			TachiResponse resp = POSTRequest("/ir/beatoraja/submit-score", json);

			return rc.create(resp.success, resp.description, null);
		}
		catch (Exception e) {
			System.out.println(e.toString());
			return rc.create(false, "Internal Exception", null);
		}
	}

	class CourseData {
		public IRCourseData course;
		public IRScoreData score;
		CourseData(IRCourseData crs, IRScoreData scr) {
			score = scr;
			course = crs;
		}
	}

	/**
	 * Sends course play info to the server via submit-course.
	 */
	public IRResponse<Object> sendCoursePlayData(IRCourseData course, IRScoreData score) {
		ResponseCreator<Object> rc = new ResponseCreator<Object>();

		CourseData courseData = new CourseData(course, score);

		try {
			ObjectWriter ow = new ObjectMapper().writer();
			String json = ow.writeValueAsString(courseData);
	
			TachiResponse resp = POSTRequest("/ir/beatoraja/submit-course", json);
	
			return rc.create(resp.success, resp.description, null);
		}
		catch (Exception e) {
			System.out.println(e.toString());
			return rc.create(false, "Internal Exception", null);
		}
	}

	/**
	 * Retrieves other scores on this chart.
	 * 
	 * @warn Beatoraja MANDATES that every single record on this chart
	 * is returned. If Tachi ever blows up to LR2IR scale, this function
	 * will obliterate both the IR and itself, and aggressive caching
	 * will have to be invoked. This is a future problem, though.
	 */
	public IRResponse<IRScoreData[]> getPlayData(IRPlayerData irpd, IRChartData model) {
		ResponseCreator<IRScoreData[]> rc = new ResponseCreator<IRScoreData[]>();

		try {
			TachiResponse resp = GETRequest("/ir/beatoraja/chart/" + model.sha256 + "/scores");

			if (!resp.success) {
				return rc.create(false, "No chart data.", null);
			}

			ArrayList<IRScoreData> irScoreDatum = new ArrayList<IRScoreData>();

			for (final JsonNode objNode : resp.body) {
				ScoreData scoreData = new ScoreData();

				// Yeah, this is just java.
				scoreData.setDate(Long.valueOf(objNode.get("date").asInt()));
				scoreData.setPlayer(objNode.get("player").asText());
				scoreData.setSha256(objNode.get("sha256").asText());
				scoreData.setGauge(objNode.get("gauge").asInt());
				scoreData.setEpg(objNode.get("epg").asInt());
				scoreData.setLpg(objNode.get("lpg").asInt());
				scoreData.setEgr(objNode.get("egr").asInt());
				scoreData.setLgr(objNode.get("lgr").asInt());
				scoreData.setEgd(objNode.get("egd").asInt());
				scoreData.setLgd(objNode.get("lgd").asInt());
				scoreData.setEbd(objNode.get("ebd").asInt());
				scoreData.setLbd(objNode.get("lbd").asInt());
				scoreData.setEpr(objNode.get("epr").asInt());
				scoreData.setLpr(objNode.get("lpr").asInt());
				scoreData.setEms(objNode.get("ems").asInt());
				scoreData.setLms(objNode.get("lms").asInt());
				scoreData.setNotes(objNode.get("notes").asInt());
				scoreData.setPassnotes(objNode.get("passnotes").asInt());
				scoreData.setClear(objNode.get("clear").asInt());
				scoreData.setPlaycount(objNode.get("playcount").asInt());
				scoreData.setRandom(objNode.get("random").asInt());
				scoreData.setMinbp(objNode.get("minbp").asInt());
				scoreData.setCombo(objNode.get("maxcombo").asInt());
				scoreData.setMode(0);

				IRScoreData irsc = new IRScoreData(scoreData);

				irScoreDatum.add(irsc);
			}

			// weird java oddities: [0] instantiates a list faster than prealloc
			IRScoreData[] irScoreArr = irScoreDatum.toArray(new IRScoreData[0]);

			// Beatoraja expects these to be sorted.
			Arrays.sort(irScoreArr, (a,b) -> b.getExscore() - a.getExscore());

			return rc.create(resp.success, resp.description, irScoreArr);
		}
		catch (Exception e) {
			log("An error has occured while fetching scores for " + model.title + " (" + model.sha256 + ")");
			e.printStackTrace(System.out);
			return rc.create(false, "Internal Exception", null);
		}
	}

	public IRResponse<IRPlayerData[]> getRivals() {
		// @todo Implement getRivals.
		ResponseCreator<IRPlayerData[]> rc = new ResponseCreator<IRPlayerData[]>();
		return rc.create(false, "Unimplemented.", new IRPlayerData[0]);
	}

	public IRResponse<IRTableData[]> getTableDatas() {
		// Not entirely sure what this method is for. No todo here.*
		ResponseCreator<IRTableData[]> rc = new ResponseCreator<IRTableData[]>();
		return rc.create(false, "Unimplemented.", new IRTableData[0]);
	}

	public IRResponse<IRScoreData[]> getCoursePlayData(IRPlayerData irpd, IRCourseData course) {
		// This will never be possible in Tachi.
		ResponseCreator<IRScoreData[]> rc = new ResponseCreator<IRScoreData[]>();
		return rc.create(false, "Unimplemented.", new IRScoreData[0]);
	}

	public String getSongURL(IRChartData chart) {
		// @todo Implement getSongUrl
		return null;
	}
	public String getCourseURL(IRCourseData course) { return null; }
	public String getPlayerURL(IRPlayerData irpd) {
		// @todo Implement getPlayerUrl
		return null;
	}
}
