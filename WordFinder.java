package cp;

import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

class Resulttemp implements Result {
    int line;
    Path path;
    Resulttemp(int linenr,Path pathoffile){
        this.line = linenr;
        this.path = pathoffile;    }
    public int line() {        return line;    }
    public Path path() {        return path;    }
}

class Statstemp implements Stats {
    Map< String, Integer > occurrences
		= new ConcurrentHashMap<>();
    Path indir;
    Statstemp(Map< String, Integer > inputhash, Path inputdir){
        this.occurrences = inputhash;
        this.indir =inputdir;}
    
	public int occurrences( String word ) {return occurrences.get(word);}
        
	public List< Result > foundIn( String word ) {
        try {
            if (occurrences.get(word) == null) {
                return new ArrayList< Result >();
            } else if (occurrences.get(word) == 1) { 
                List< Result > ls = new ArrayList< Result >();
                ls.add(WordFinder.findAny(word, this.indir)); return ls;} 
            else { return WordFinder.findAll(word,this.indir);}
            } catch (Exception e) {
                return null;  }
}
	public String mostFrequent() {
            String word = "";
            int count = 0;
            for (Map.Entry<String,Integer> ety : this.occurrences.entrySet()) {
                if (ety.getValue()>count) {
                    count = ety.getValue();
                    word = ety.getKey();
                }}
            return word;  }
        
	public String leastFrequent() {
            String word = "";
            int count = 0;
            for (Map.Entry<String,Integer> ety : this.occurrences.entrySet()) {
                if (count == 0) {
                   count = ety.getValue();
                   word = ety.getKey();
                }
                if (ety.getValue()<count) {
                    count = ety.getValue();
                    word = ety.getKey();
                }     }
            return word;      }
        
	public List< String > words() { return new ArrayList< String >(occurrences.keySet());}
	
	public List< String > wordsByOccurrences() {
            List<Map.Entry<String,Integer>> list = new LinkedList<Map.Entry<String,Integer>>(this.occurrences.entrySet());
            Collections.sort(list, new Comparator<Map.Entry<String,Integer>>(){
            public int compare(Map.Entry<String,Integer> o1,Map.Entry<String,Integer> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }  });
            List< String > sortedList = new ArrayList< String >();
            for (Map.Entry<String,Integer> ety : list) {
                sortedList.add(ety.getKey());
            }
            return sortedList;    }
}

public class WordFinder
{
    private static int number_of_cores = Runtime.getRuntime().availableProcessors();
	
        public static List< Result > findAll( String word, Path dir ) throws Exception
	{ ExecutorService localexecutor =
                Executors.newFixedThreadPool( number_of_cores+1 );
            List < Path > pathlist = visit(dir);
            List< Result > lt = Collections.synchronizedList(new ArrayList< Result >());
            for ( Path path : pathlist) {
                BufferedReader reader = Files.newBufferedReader( path );
                String line;
                int linenr = 0;
                List< Future< List< Result > > > results = new ArrayList<>();
                while( (line = reader.readLine()) != null ) {
                        linenr++;
                        final Path currentpath = path; 
                        final int currentLinenr = linenr;
                        final String currentLine = line;
                        try { 
                        Future< List< Result > > f = localexecutor.submit(
                                () ->  {
                                    String[] t = currentLine.split( "\\s+" );
                                                List< Result > llt = Collections.synchronizedList(new ArrayList< Result >());
                                                for (String str : t) {
                                                    if (str.equals(word)) {
                                                        llt.add(new Resulttemp(currentLinenr,currentpath));
                                                    }
                                                };
                                                return llt;
                                }                                                        
                        );    
                        results.add( f );
                        } catch (Exception e) { }
                }
                for( Future< List< Result > > f : results ) {
                lt.addAll(f.get());
                }
            }
            localexecutor.shutdown();
            localexecutor.awaitTermination( 1, TimeUnit.DAYS );
            return lt;
        }
        
	public static Result findAny( String word, Path dir ) throws Exception
	{ ExecutorService localexecutor =
                Executors.newFixedThreadPool( number_of_cores+1 );
            List < Path > pathlist = visit(dir);
            List< Result > lt = Collections.synchronizedList(new ArrayList< Result >());
            for ( Path path : pathlist) {
                if (localexecutor.isShutdown()) {
                    break;    }
                BufferedReader reader = Files.newBufferedReader( path );
                String line;
                int linenr = 0;
                List< Future< List< Result > > > results = new ArrayList<>();
                while( (line = reader.readLine()) != null && !(localexecutor.isShutdown())) {
                        linenr++;
                        final Path currentpath = path;
                        final int currentLinenr = linenr;
                        final String currentLine = line;
                        try { 
                        Future< List< Result > > f = localexecutor.submit(
                                () ->  {
                                    String[] t = currentLine.split( "\\s+" );
                                                for (String str : t) {
                                                    if (str.equals(word)) {
                                                            synchronized(lt) {
                                                                lt.add(new Resulttemp(currentLinenr,currentpath));
                                                            }
                                                            localexecutor.shutdownNow();
                                                            break;
                                                    }
                                                };
                                                return null;
                                }                                                        
                        );    
                        results.add( f );
                        } catch (Exception e) {  }
                }
            }
            localexecutor.shutdown();
            localexecutor.awaitTermination( 1, TimeUnit.DAYS );
            try { return lt.get(0);
            } catch (Exception e) {
                return new Resulttemp(-1,null);  }
        }
        
	public static Stats stats( Path dir ) throws InterruptedException
	{
            ExecutorService localexecutor =
                Executors.newFixedThreadPool( number_of_cores+1 );
            List < Path > pathlist = visit(dir);
            
            final Map< String, Integer > occurrences
		= new ConcurrentHashMap<>();
            
            for ( Path path : pathlist) {
                try {
			BufferedReader reader = Files.newBufferedReader( path );
			String line;
			while( (line = reader.readLine()) != null ) {
                                final String currentLine = line;
				localexecutor.submit(
					() -> {
                                            String[] words = currentLine.split( "\\s+" );
                                            for( String word : words ) {
                                                    occurrences.compute( word, (k,v) -> {
                                                            if ( v == null ) {
                                                                return 1;
                                                            } else {
                                                                return v + 1;
                                                            }
                                                    } );
                                            }
                                            
                                        }
				);
			}
		} catch( IOException e ) {
			e.printStackTrace();
		}
            }
            localexecutor.shutdown();
            localexecutor.awaitTermination( 1, TimeUnit.DAYS );
            return new Statstemp(occurrences,dir);
	}
        
	public static List < Path > visit( Path dir )
	{   List< Path > total = Collections.synchronizedList(new ArrayList< Path >());
		try (
			DirectoryStream< Path > dirStream = Files.newDirectoryStream( dir )
		) {
			for( Path path : dirStream ) {
				if ( Files.isDirectory( path ) ) {
					total.addAll(visit( path ));
				} else {
					if ( path.toString().endsWith( ".txt" ) ) {
						total.add(path);
					}
				}
			}
		} catch( IOException e ) {
			e.printStackTrace();
		}
		return total;
	}
}