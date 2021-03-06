package ca.mcgill.sis.dmas.nlp.exp.pan2014av;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.mcgill.sis.dmas.env.StringResources;
import ca.mcgill.sis.dmas.nlp.exp.Utils.TestEntries;
import ca.mcgill.sis.dmas.nlp.exp.Utils.TestEntry;
import ca.mcgill.sis.dmas.nlp.model.astyle.Document;
import ca.mcgill.sis.dmas.nlp.model.astyle.MathUtilities;
import ca.mcgill.sis.dmas.nlp.model.astyle._3_syntactic.*;
import ca.mcgill.sis.dmas.nlp.model.astyle._3_syntactic.LearnerSyn2VecEmbedding.S2VParam;
import ca.mcgill.sis.dmas.nlp.model.astyle._3_syntactic.LearnerSyn2VecEmbedding.SEmbedding;
import ca.mcgill.sis.dmas.nlp.model.astyle._3_syntactic.LearnerSyn2VecEmbedding2;;

public class PAN14TestPos2Vec {

	public static Logger logger = LoggerFactory.getLogger(PAN14TestPos2Vec.class);

	public static void main(String... args) throws InterruptedException, ExecutionException {
		PAN2014AV2.DS_PROCESSED_PATH = new File(args[0]).getAbsolutePath();
		System.out.println("POS model #1");
		MathUtilities.createExpTable();
		test();
	}

	public static TestEntry<S2VParam> test(PAN2014AV2 ds, S2VParam param, String testCase, String cacheFile) {

		LearnerSyn2VecEmbedding2 p2v = new LearnerSyn2VecEmbedding2(param);
		p2v.debug = false;
		p2v.iterationHood = iteration -> {
			// this is a blocking hood.
		};
		// learn representation
		LanguageDataset trainingSet = ds.trainingSet.get(testCase);
		LanguageDataset testingSet = ds.testingSet.get(testCase);
		ArrayList<Document> documents = new ArrayList<>();
		trainingSet.documents.forEach(documents::add);
		testingSet.documents.forEach(documents::add);
		Collections.shuffle(documents, new Random(0));
		// test performance on testing set
		try {
			p2v.train(documents);
			SEmbedding embd = p2v.produceDocEmbd();
			EmbeddingVerifier chr = new EmbeddingVerifier(MathUtilities.normalize(embd.synEmbedding));
			double ts = ds.test(chr, testCase);
			logger.info("{} character {}", testCase, ts);
			TestEntry<S2VParam> ent = new TestEntry<S2VParam>(ts, param);
			return ent;
		} catch (Exception e) {
			logger.error("Failed to train the model..", e);
			return new TestEntry<S2VParam>(-1, param);
		}
	}

	public static void test() throws InterruptedException, ExecutionException {

		String resultCache = StringResources.getRootPath() + "/cache/" + PAN14TestPos2Vec.class.getSimpleName() + "/";
		if (!new File(resultCache).isDirectory())
			new File(resultCache).mkdirs();
		PAN2014AV2 ds = PAN2014AV2.load(PAN2014AV2.DS_PROCESSED_PATH);

		Supplier<S2VParam> default_supplier = () -> {
			S2VParam param = new S2VParam();
			param.optm_parallelism = 1;
			param.optm_aphaUpdateInterval = -1;
			return param;
		};

		ForkJoinPool pool = new ForkJoinPool(30);

		pool.submit(() -> {
			ds.trainingSet.keySet().parallelStream().forEach(testcase -> {
				String resultCatchFile = resultCache + testcase + ".json";
				S2VParam last_max_param = TestEntries.currentMax(resultCatchFile, S2VParam.class);
				if (last_max_param != null) {
					logger.info("Testing {} with validation param", testcase);
					TestEntry<S2VParam> max = test(ds, last_max_param, testcase, resultCatchFile);
					logger.info(" {} score: {}", testcase, max.score);
					return;
				}
			});
		}).get();
	}

}
