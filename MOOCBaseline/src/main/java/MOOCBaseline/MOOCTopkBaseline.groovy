package MOOCBaseline

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.collect.Iterables

import java.util.concurrent.atomic.AtomicBoolean;

import edu.umd.cs.bachuai13.util.DataOutputter;
import edu.umd.cs.bachuai13.util.ExperimentConfigGenerator;
import edu.umd.cs.bachuai13.util.FoldUtils;
import edu.umd.cs.bachuai13.util.GroundingWrapper;

import edu.umd.cs.psl.application.inference.LazyMPEInference;
import edu.umd.cs.psl.application.inference.MPEInference
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.LazyMaxLikelihoodMPE;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxPseudoLikelihood
import edu.umd.cs.psl.application.learning.weight.maxmargin.MaxMargin
import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.database.ResultList
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import edu.umd.cs.psl.evaluation.result.*
import edu.umd.cs.psl.evaluation.statistics.DiscretePredictionComparator
import edu.umd.cs.psl.evaluation.statistics.RankingScore
import edu.umd.cs.psl.evaluation.statistics.SimpleRankingComparator
import edu.umd.cs.psl.evaluation.statistics.DiscretePredictionStatistics.BinaryClass;
import edu.umd.cs.psl.groovy.PSLModel;
import edu.umd.cs.psl.groovy.PredicateConstraint;
import edu.umd.cs.psl.groovy.SetComparison;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.ui.functions.textsimilarity.*
import edu.umd.cs.psl.ui.loading.InserterUtils;
import edu.umd.cs.psl.util.database.Queries;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel
import edu.umd.cs.psl.model.parameters.Weight
import edu.umd.cs.psl.model.predicate.Predicate;
println "PSL Model for MOOC -- Baseline"

//config manager

ConfigManager cm = ConfigManager.getManager()
ConfigBundle config = cm.getBundle("mooc-baseline-1")
Logger log = LoggerFactory.getLogger(this.class)
ExperimentConfigGenerator configGenerator = new ExperimentConfigGenerator("mooc-top-k-baseline-1");
configGenerator.setLearningMethods(["MLE", "MPLE", "MM"]);

//database

def defaultPath = System.getProperty("java.io.tmpdir")
String dbpath = config.getString("dbpath", defaultPath + File.separator + "mooc-top-k-baseline-1")
DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), config)

PSLModel m = new PSLModel(this, data)
m.add predicate: "performance" , types: [ArgumentType.UniqueID]
//m.add predicate: "topkperformance" , types: [ArgumentType.UniqueID]

def trainDir = 'data'+java.io.File.separator+'disr'+java.io.File.separator + 'start-end-all' + java.io.File.separator//+'till-t'+(timePeriod-1)+java.io.File.separator
Partition truthPart = new Partition(0)
Partition targetPart = new Partition(1)
Partition dummy2 = new Partition(201)
for (Predicate p : [performance])
{
	println "\t\t\tREADING " + p.getName() +" ...";
	insert = data.getInserter(p, truthPart)
	println trainDir+"performance.txt"
	InserterUtils.loadDelimitedDataTruth(insert, trainDir+"performance.txt");
	insert1 = data.getInserter(p, targetPart)
	println trainDir+"lecturerankallperformance.txt"
	InserterUtils.loadDelimitedDataTruth(insert1, trainDir+"lecturerankallperformance.txt");
}

def resultsDB = data.getDatabase(targetPart, [performance] as Set)
def comparator = new SimpleRankingComparator(resultsDB)
def groundTruthDB = data.getDatabase(truthPart, [performance] as Set)
comparator.setBaseline(groundTruthDB)

def metrics = [RankingScore.AUPRC, RankingScore.NegAUPRC, RankingScore.AreaROC, RankingScore.Kendall]

double [] score = new double[metrics.size()]

for (int i = 0; i < metrics.size(); i++) {
	comparator.setRankingScore(metrics.get(i))
	score[i] = comparator.compare(performance)
}
System.out.println("Area under positive-class PR curve: " + score[0])
System.out.println("Area under negative-class PR curve: " + score[1])
System.out.println("Area under ROC curve: " + score[2])
System.out.println("Kendall's: " + score[3])

resultsDB.close()
groundTruthDB.close()

