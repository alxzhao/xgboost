package ml.dmlc.xgboost4j.demo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ml.dmlc.xgboost4j.*;


/**
 * Distributed training example, used to quick test distributed training.
 *
 * @author tqchen
 */
public class DistTrain {
  private static final Log logger = LogFactory.getLog(DistTrain.class);
  private Map<String, String> envs = null;

  private class Worker implements Runnable {
    private int worker_id;
    Worker(int worker_id) {
      this.worker_id = worker_id;
    }

    public void run() {
      try {
        Map<String, String> worker_env = new HashMap<String, String>(envs);

        worker_env.put("DMLC_TASK_ID", new Integer(worker_id).toString());
        // always initialize rabit module before training.
        Rabit.init(worker_env);

        // load file from text file, also binary buffer generated by xgboost4j
        DMatrix trainMat = new DMatrix("../../demo/data/agaricus.txt.train");
        DMatrix testMat = new DMatrix("../../demo/data/agaricus.txt.test");

        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("eta", 1.0);
        params.put("max_depth", 2);
        params.put("silent", 1);
        params.put("nthread", 2);
        params.put("objective", "binary:logistic");


        HashMap<String, DMatrix> watches = new HashMap<String, DMatrix>();
        watches.put("train", trainMat);
        watches.put("test", testMat);

        //set round
        int round = 2;

        //train a boost model
        Booster booster = XGBoost.train(params, trainMat, round, watches, null, null);

        // always shutdown rabit module after training.
        Rabit.shutdown();
      } catch (Exception ex){
        logger.error(ex);
      }
    }
  }

  void start(int nworker) throws IOException, XGBoostError, InterruptedException {
    RabitTracker tracker = new RabitTracker(nworker);
    tracker.start();
    envs = tracker.getWorkerEnvs();
    for (int i = 0; i < nworker; ++i) {
      new Thread(new Worker(i)).start();
    }
    tracker.waitFor();
  }

  public static void main(String[] args) throws IOException, XGBoostError, InterruptedException {
    new DistTrain().start(Integer.parseInt(args[0]));
  }
}