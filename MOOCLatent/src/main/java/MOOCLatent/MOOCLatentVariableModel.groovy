package MOOCLatent;

println "This source file is a place holder for the tree of groovy and java sources for your PSL project."

import java.util.concurrent.atomic.AtomicBoolean;

import edu.umd.cs.psl.application.inference.LazyMPEInference;
import edu.umd.cs.psl.application.inference.MPEInference
import edu.umd.cs.psl.application.learning.weight.em.HardEM
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.LazyMaxLikelihoodMPE;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxPseudoLikelihood
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.VotedPerceptron
import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.database.ResultList
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import edu.umd.cs.psl.groovy.PSLModel;
import edu.umd.cs.psl.groovy.PredicateConstraint;
import edu.umd.cs.psl.groovy.SetComparison;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.UniqueID
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.ui.functions.textsimilarity.*
import edu.umd.cs.psl.ui.loading.InserterUtils;
import edu.umd.cs.psl.util.database.Queries;
import edu.umd.cs.psl.model.predicate.Predicate;

println "PSL Model for MOOC"

//config manager

ConfigManager cm = ConfigManager.getManager()
ConfigBundle config = cm.getBundle("mooc-example")

//database

def defaultPath = System.getProperty("java.io.tmpdir")
String dbpath = config.getString("dbpath", defaultPath + File.separator + "basic-example")
DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), config)

PSLModel m = new PSLModel(this, data)

//Aggregate predicates
m.add predicate: "postActivity" , types: [ArgumentType.UniqueID]
m.add predicate: "viewActivity" , types: [ArgumentType.UniqueID]
m.add predicate: "voteActivity" , types: [ArgumentType.UniqueID]
m.add predicate: "reputation" , types: [ArgumentType.UniqueID]
m.add predicate: "ontime" , types: [ArgumentType.UniqueID]
m.add predicate: "submitted" , types: [ArgumentType.UniqueID]

//Post-level predicates

m.add predicate: "posts" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "votes" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "views" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "upvote" , types: [ArgumentType.UniqueID]
m.add predicate: "downvote" , types: [ArgumentType.UniqueID]
//m.add predicate: "reply" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

//Type predicates
m.add predicate: "post" , types: [ArgumentType.UniqueID]
m.add predicate: "user" , types: [ArgumentType.UniqueID]

m.add predicate: "polarity" , types: [ArgumentType.UniqueID]
m.add predicate: "subjective" , types: [ArgumentType.UniqueID]
//m.add predicate: "negative" , types: [ArgumentType.UniqueID]
m.add predicate: "inThread" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

m.add predicate: "performance" , types: [ArgumentType.UniqueID]

//Engagement predicates
m.add predicate: "engagement_active" , types: [ArgumentType.UniqueID]
m.add predicate: "engagement_passive" , types: [ArgumentType.UniqueID]

//rules
//post activity indicates performance
m.add rule : ( postActivity(U) ) >> engagement_active(U),  weight : 5
m.add rule : ( user(U) & ~postActivity(U) ) >> ~engagement_active(U),  weight : 5
//vote activity indicates performance

m.add rule : ( voteActivity(U) ) >> engagement_passive(U),  weight : 5
m.add rule : ( user(U) & ~voteActivity(U) ) >> ~engagement_passive(U),  weight : 5

//vote activity and post activity indicates performance

m.add rule : ( viewActivity(U) ) >> engagement_passive(U),  weight : 5
m.add rule : ( user(U) & ~viewActivity(U) ) >> ~engagement_passive(U),  weight : 5

//all activity indicates performance
//m.add rule : ( postActivity(U) & viewActivity(U) & voteActivity(U) ) >> performance(U),  weight : 5

//reputation indicates performance
m.add rule : ( engagement_active(U) & reputation(U) ) >> performance(U),  weight : 5
m.add rule : ( engagement_active(U) & user(U) & ~reputation(U) ) >> ~performance(U),  weight : 5

//posts positive stuff
m.add rule : ( posts(U, P) & polarity(P)) >> engagement_active(U),  weight : 5
m.add rule : ( posts(U, P) & post(P) & ~polarity(P)) >> ~engagement_active(U),  weight : 5

//posts stuff that has positive votes
m.add rule : ( posts(U, P) & upvote(P)) >> engagement_active(U),  weight : 5
m.add rule : ( posts(U, P) & post(P) & ~upvote(P)) >> ~engagement_active(U),  weight : 5

//posts stuff that has negative votes
m.add rule : ( posts(U, P) & downvote(P)) >> engagement_active(U),  weight : 5

//ontime with the course
m.add rule : ( engagement_passive(U) ) >> performance(U),  weight : 15
m.add rule : ( engagement_active(U) ) >> performance(U),  weight : 15
m.add rule : ( ~engagement_active(U) ) >> ~performance(U), weight : 20

m.add rule : ( user(U) & ~submitted(U) ) >> ~engagement_active(U),  weight : 5
m.add rule : ( user(U) & ~ontime(U) ) >> ~engagement_active(U),  weight : 5

//network rules

m.add rule : ( posts(U1, P1) & posts(U2, P2) & performance(U1) & inThread(U1, T) & inThread(U2, T) ) >> performance(U2),  weight : 5


//Read data in database
println "reading data"
Partition trainPart = new Partition(0);
def trainDir = 'data-new-id'+java.io.File.separator+'train'+java.io.File.separator;


for (Predicate p : [postActivity, ontime, reputation, polarity, subjective, submitted, viewActivity, voteActivity])
{
	println "\t\t\tREADING " + p.getName() +" ...";
	insert = data.getInserter(p, trainPart)
	println trainDir+p.getName()+".txt"
	InserterUtils.loadDelimitedDataTruth(insert, trainDir+p.getName()+".txt");
}

for (Predicate p : [user, post, upvote, downvote, posts])
{
	println "\t\t\tREADING " + p.getName() +" ...";
	insert = data.getInserter(p, trainPart)
	println trainDir+p.getName()+".txt"
	InserterUtils.loadDelimitedData(insert, trainDir+p.getName()+".txt");
}

println m

Database db = data.getDatabase(trainPart, [postActivity, ontime, reputation, polarity, subjective, submitted, user, post, upvote, downvote, posts, viewActivity, voteActivity] as Set);

ResultList userList = db.executeQuery(Queries.getQueryForAllAtoms(user))

//LazyMPEInference inferenceApp = new LazyMPEInference(m, db, config);
//inferenceApp.mpeInference();
//inferenceApp.close();
//
//println "Inference results with hand-defined weights:"
//
//for (GroundAtom atom : Queries.getAllAtoms(db, performance)){
//	println atom.toString() + "\t" + atom.getValue();
//}

Partition trueDataPartition = new Partition(1);
insert = data.getInserter(performance, trueDataPartition)
InserterUtils.loadDelimitedDataTruth(insert, trainDir + "PERFORMANCE.txt");

Database trueDataDB = data.getDatabase(trueDataPartition, [performance] as Set);

for (Predicate p : [engagement_active, engagement_passive, performance]) {
	for (int i = 0; i< userList.size(); i++) {
		GroundTerm[] grounding = userList.get(i);
		RandomVariableAtom lv = (RandomVariableAtom) db.getAtom(p, grounding)
		lv.commitToDB()
	}
}



HardEM weightLearning = new HardEM(m, db, trueDataDB, config);

weightLearning.learn();
weightLearning.close();

println "Learned model:"
println m

LazyMPEInference inferenceApp = new LazyMPEInference(m, db, config);
inferenceApp.mpeInference();
inferenceApp.close();

for (GroundAtom atom : Queries.getAllAtoms(db, performance))
	println atom.toString() + "\t" + atom.getValue();

for (GroundAtom atom : Queries.getAllAtoms(db, engagement_active))
	println atom.toString() + "\t" + atom.getValue();

for (GroundAtom atom : Queries.getAllAtoms(db, engagement_passive))
	println atom.toString() + "\t" + atom.getValue();

	
	//test data
//Partition testPart = new Partition(2);
//
//def testDir = 'data-new-id'+java.io.File.separator+'test'+java.io.File.separator;
//
//
//for (Predicate p : [postActivity, ontime, reputation, polarity, subjective, submitted])
//{
//	println "\t\t\tREADING " + p.getName() +" ...";
//	insert = data.getInserter(p, testPart)
//	println testDir+p.getName()+".txt"
//	InserterUtils.loadDelimitedDataTruth(insert, testDir+p.getName().toLowerCase()+".txt");
//}
//
//for (Predicate p : [user, post, upvote, downvote, posts])
//{
//	println "\t\t\tREADING " + p.getName() +" ...";
//	insert = data.getInserter(p, testPart)
//	println testDir+p.getName()+".txt"
//	InserterUtils.loadDelimitedData(insert, testDir+p.getName().toLowerCase()+".txt");
//}
//
//Database db2 = data.getDatabase(testPart, [postActivity, ontime, reputation, polarity, subjective, submitted, user, post, upvote, downvote, posts] as Set);
//inferenceApp = new LazyMPEInference(m, db2, config);
//result = inferenceApp.mpeInference();
//inferenceApp.close();
//
//println "Inference results on second social network with learned weights:"
//for (GroundAtom atom : Queries.getAllAtoms(db2, performance))
//	println atom.toString().substring(13, 16) + "\t" + atom.getValue();
////println result
///* We close the Databases to flush writes */
//db.close();
//trueDataDB.close();
//db2.close();