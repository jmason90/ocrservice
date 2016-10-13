package in.rkvsraman.indicocr.webservice;

import java.io.File;
import java.io.IOException;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class App extends AbstractVerticle {

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		JsonObject object = new JsonObject();
		object.put("scribo_path", args[0]);
		object.put("http.port", 8081);

		Vertx.vertx().deployVerticle(App.class.getName(), new DeploymentOptions().setConfig(object));

	}

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		// TODO Auto-generated method stub

		System.out.println("ScriboPath:" + config().getString("scribo_path"));
		startWebApp(startFuture);
	}

	private void startWebApp(Future<Void> startFuture) {
		// Create a router object.
		Router router = Router.router(vertx);

		// Bind "/" to our hello message.
		router.route().handler(BodyHandler.create());
		router.get("/").handler(routingContext -> {
			HttpServerResponse response = routingContext.response();
			response.putHeader("content-type", "text/html")
					.end("<h1>Indic - OCR Service</h1> <h2> By RKVS Raman</h2> ");
		});

		router.post("/ocr").handler(this::getAll);

		// Create the HTTP server and pass the "accept" method to the request
		// handler.
		vertx.createHttpServer().requestHandler(router::accept).listen(

				config().getInteger("http.port", 8080), result -> {
					if (result.succeeded()) {
						startFuture.complete();
					} else {
						startFuture.fail(result.cause());
					}
				});
	}

	private void getAll(RoutingContext routingContext) {

		if (routingContext.fileUploads().size() < 1) {
			routingContext.response().end("No file attached.\n");
			return;
		}
		String filePath = new String();
		for (FileUpload f : routingContext.fileUploads()) {

			filePath = f.uploadedFileName();
			break;
		}
		File f = new File(filePath);
		String mimetype = new MimetypesFileTypeMap().getContentType(f);
		String type = mimetype.split("/")[0];
		if (!type.equals("image")) {
			routingContext.response().end("Uploaded file is not an image.\n");
			return;
		}

		String lang = routingContext.request().getFormAttribute("lang");
		if (lang == null || lang.length() == 0) {
			routingContext.response().end("No language specified.\n");
			return;
		}

		if (filePath.length() < 0) {
			routingContext.response().end("Could not retrieve uploaded file.\n");
			return;
		}

		convertToODTAndSend(routingContext, filePath, lang);

	}

	private void convertToODTAndSend(RoutingContext routingContext, String filePath, String lang) {

		CommandLine command = new CommandLine(config().getString("scribo_path"));

		File outputfile;
		try {
			outputfile = File.createTempFile("fromweb", ".xml");
		} catch (IOException e) {
			routingContext.response().end("Could not create intermediate files.\n");
			e.printStackTrace();
			return;
		}
		command.addArguments(filePath + " " + outputfile.getAbsolutePath() + " --ocr-lang " + lang);

		ExecuteWatchdog watchDog = new ExecuteWatchdog(30000); // Not more than
																// 30 seconds

		DefaultExecutor executor = new DefaultExecutor();
		executor.setWatchdog(watchDog);

		ScriboHandler handler = new ScriboHandler(routingContext, filePath, outputfile, watchDog, command,lang);

		try {
			executor.execute(command, handler);
		} catch (ExecuteException e) {
			routingContext.response().end("Some problem in intermediate process.\n");
			e.printStackTrace();
			return;
		} catch (IOException e) {
			routingContext.response().end("IO Exception in intermediate process.\n");
			e.printStackTrace();
			return;
		}

	}

}
