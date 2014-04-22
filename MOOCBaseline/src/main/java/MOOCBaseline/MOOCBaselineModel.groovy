package MOOCBaseline;

println "This source file is a place holder for the tree of groovy and java sources for your PSL project."

//PSL Model 1 for Student performance
//Target : performance

import java.util.concurrent.atomic.AtomicBoolean;

import edu.umd.cs.bachuai13.util.DataOutputter;
import edu.umd.cs.bachuai13.util.ExperimentConfigGenerator;
import edu.umd.cs.bachuai13.util.FoldUtils;
import edu.umd.cs.bachuai13.util.GroundingWrapper;

import edu.umd.cs.psl.application.inference.LazyMPEInference;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.LazyMaxLikelihoodMPE;
import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import edu.umd.cs.psl.groovy.PSLModel;
import edu.umd.cs.psl.groovy.PredicateConstraint;
import edu.umd.cs.psl.groovy.SetComparison;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.ui.functions.textsimilarity.*
import edu.umd.cs.psl.ui.loading.InserterUtils;
import edu.umd.cs.psl.util.database.Queries;
import edu.umd.cs.psl.model.predicate.Predicate;

println "PSL Model for MOOC -- Baseline"

//config manager

ConfigManager cm = ConfigManager.getManager()
ConfigBundle config = cm.getBundle("mooc-baseline")

//database

def defaultPath = System.getProperty("java.io.tmpdir")
String dbpath = config.getString("dbpath", defaultPath + File.separator + "mooc-baseline")
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

//rules
//post activity indicates performance
m.add rule : ( postActivity(U) ) >> performance(U),  weight : 5
m.add rule : ( user(U) & ~postActivity(U) ) >> ~performance(U),  weight : 5
//vote activity indicates performance

m.add rule : ( voteActivity(U) ) >> performance(U),  weight : 5
m.add rule : ( user(U) & ~voteActivity(U) ) >> ~performance(U),  weight : 5

//vote activity and post activity indicates performance

m.add rule : ( viewActivity(U) ) >> performance(U),  weight : 5
m.add rule : ( user(U) & ~viewActivity(U) ) >> ~performance(U),  weight : 5

//all activity indicates performance
m.add rule : ( postActivity(U) & viewActivity(U) & voteActivity(U) ) >> performance(U),  weight : 5

//reputation indicates performance
m.add rule : ( reputation(U) ) >> performance(U),  weight : 5
m.add rule : ( user(U) & ~reputation(U) ) >> ~performance(U),  weight : 5

m.add rule : ( postActivity(U) & reputation(U) ) >> performance(U),  weight : 10
m.add rule : ( postActivity(U) & user(U) & ~reputation(U) ) >> ~performance(U),  weight : 10

//posts positive stuff
m.add rule : ( posts(U, P) & polarity(P)) >> performance(U),  weight : 5
m.add rule : ( posts(U, P) & post(P) & ~polarity(P)) >> ~performance(U),  weight : 5

//posts stuff that has positive votes
m.add rule : ( posts(U, P) & upvote(P)) >> performance(U),  weight : 5
m.add rule : ( posts(U, P) & post(P) & ~upvote(P)) >> performance(U),  weight : 5

//posts stuff that has negative votes
m.add rule : ( posts(U, P) & downvote(P)) >> ~performance(U),  weight : 5

//ontime with the course
m.add rule : ( ontime(U) ) >> performance(U),  weight : 5
m.add rule : ( submitted(U) ) >> performance(U),  weight : 5
m.add rule : ( user(U) & ~submitted(U) ) >> ~performance(U),  weight : 5
m.add rule : ( user(U) & ~ontime(U) ) >> ~performance(U),  weight : 5

//network rules

m.add rule : ( posts(U1, P1) & posts(U2, P2) & performance(U1) & inThread(U1, T) & inThread(U2, T) ) >> performance(U2),  weight : 5

//Read data in database
println "reading data"
List<Partition> fullData = new ArrayList<Partition>(14);
List<Predicate> predicates = new ArrayList<Predicate>(14);
Partition fullPosts = new Partition(10000);

def trainDir = 'data'+java.io.File.separator //+'train'+java.io.File.separator;
for(int i = 0; i< 14; i++) {
	fullData.add(i, new Partition(i))
}
def counter = 0
for (Predicate p : [postActivity, ontime, reputation, polarity, subjective, submitted, viewActivity, voteActivity])
{
	println "\t\t\tREADING " + p.getName() +" ...";
	insert = data.getInserter(p, fullData.get(counter))
	println trainDir+p.getName().toLowerCase()+".txt"
	InserterUtils.loadDelimitedDataTruth(insert, trainDir+p.getName().toLowerCase()+".txt");
	predicates.add(counter, p)
	println counter
	counter = counter + 1
}
println "counter 1 " + counter
for (Predicate p : [user, post, upvote, downvote])
{
	println "\t\t\tREADING " + p.getName() +" ...";
	insert = data.getInserter(p, fullData.get(counter))
	println trainDir+p.getName().toString().toLowerCase()+".txt"
	InserterUtils.loadDelimitedData(insert, trainDir+p.getName().toString().toLowerCase()+".txt");
	predicates.add(counter, p)
	println counter
	counter = counter + 1
}
println "counter 2 " + counter
for (Predicate p : [performance])
{
	println "\t\t\tREADING " + p.getName() +" ...";
	insert = data.getInserter(p, fullData.get(counter))
	println trainDir+p.getName().toString().toLowerCase()+".txt"
	InserterUtils.loadDelimitedDataTruth(insert, trainDir+p.getName().toString().toLowerCase()+".txt");
	predicates.add(counter, p)
	println counter
	counter = counter + 1
}

for (Predicate p : [posts])
{
	println "\t\t\tREADING " + p.getName() +" ...";
	insert = data.getInserter(p, fullPosts)
	println trainDir+p.getName().toString().toLowerCase()+".txt"
	InserterUtils.loadDelimitedData(insert, trainDir+p.getName().toString().toLowerCase()+".txt");
}
println "counter 3 " + counter
//split data for crossvalidation
folds = 10
List<Partition> dataPartitions = new ArrayList<Partition>(folds*13)
List<Partition> dataPostPartitions = new ArrayList<Partition>(folds)
//List<Partition> targetPartitions = new ArrayList<Partition>(folds)
//List<Partition> trainWritePartitions = new ArrayList<Partition>(folds)
//List<Partition> testWritePartitions = new ArrayList<Partition>(folds)
//List<Partition> trainPriorPartitions = new ArrayList<Partition>(folds)
//List<Partition> testPriorPartitions = new ArrayList<Partition>(folds)
counter = 0
for (int i = 0; i < folds; i++) {
	for(int j = 0; j < 13; j++) {
		dataPartitions.add(counter, new Partition((i+1)*13 + j))
		counter +=1
	}
}
postCount = 0;
for (int i=0; i< folds; i++) {
	dataPostPartitions.add(i, new Partition(100 + i))
}
counter = 0
println predicates.size()
println fullData.size()
List<Predicate> preds = [predicates.get(0), predicates.get(1)] 
List<Set<GroundingWrapper>> groundings = FoldUtils.splitGroundings(data, predicates, fullData, folds)

//[predicates.get(0), predicates.get(1), predicates.get(2), 
//	predicates.get(3), predicates.get(4), predicates.get(5), predicates.get(6), predicates.get(7), predicates.get(8), predicates.get(9), predicates.get(10), predicates.get(11), predicates.get(12)], 
//[fullData.get(0), fullData.get(1), fullData.get(2), fullData.get(3), fullData.get(4), fullData.get(5), fullData.get(6), fullData.get(7), fullData.get(8), fullData.get(9), fullData.get(10), 
//	fullData.get(11), fullData.get(12)], folds)

List<Set<GroundingWrapper>> groundingsPosts = FoldUtils.splitGroundings(data, [posts], [fullPosts], folds)

println groundings.size()
for (int i = 0; i < folds; i++) {
	
	for(int j = 0; j< 13; j++) {
		//println dataPartitions.get(j)
		println "fold : " + i
		FoldUtils.copy(data, fullData.get(j), dataPartitions.get(counter), predicates.get(j), groundings.get(i))
		counter+=1
	}
}

for (int i = 0; i < folds; i++) {
	FoldUtils.copy(data, fullPosts, dataPostPartitions.get(i), performance, groundingsPosts.get(i))
}
