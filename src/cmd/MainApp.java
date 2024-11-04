package cmd;
import java.io.File;
//BT3 GSC BGM Detector v1.1 by ViveTheModder
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Scanner;

public class MainApp 
{
	static RandomAccessFile bgmCSV, gsc;
	static ArrayList<BT3Cutscene> gsacList = null;
	static ArrayList<Short> bgmList = null, addrList = null;
	static ArrayList<String> bgmNames = null;
	static double totalTime;
	public static boolean isFaultyGSC() throws IOException
	{
		boolean gscError = false;
		//24576 is the reserved space of Unexpected Help - anything bigger won't even work in-game so why use the tool lol
		if (gsc.length()>24576) return true;
		gsc.seek(0); //added this just to make sure it always starts from 0
		int gscf = gsc.readInt(); //GSCF (Game Scenario Contents of File)
		if (gscf != 0x47534346) gscError = true; 
		
		gsc.seek(8);
		short gscSize = LittleEndian.getShort(gsc.readShort());
		if (gscSize+32 != gsc.length()) gscError = true;
		
		gsc.seek(16); 
		int gshd = gsc.readInt(); //GSHD (GSC version indicator)
		if (gshd != 0x47534844) gscError = true; 
		gsc.seek(32);
		
		/* gscVer values:
		 * v3.1 (0x0300000001000000) -> Budokai Tenkaichi 2
		 * v3.2 (0x0300000002000000) -> Budokai Tenkaichi 3
		 * v3.3 (0x0300000002000000) -> Raging Blast 1 */ 
		long gscVer = gsc.readLong();
		if (gscVer != 0x0300000002000000L) gscError = true; 
		
		gsc.seek(64);
		int gscd = gsc.readInt(); //GSCD (contains total file size of all scenes)
		if (gscd != 0x47534344) gscError = true;

		gsc.seek(72);
		short gsacTotalSize = LittleEndian.getShort(gsc.readShort());
		gsc.seek(gsacTotalSize+96); //112 is the actual size of the header, but I put 96 bc of the next check
		
		if (gsc.readInt() != 0x47534454) gscError = true; //0x47534454 = GSDT (Game Scenario DaTa)
		return gscError;
	}
	public static void detectSongsInGSC() throws IOException
	{
		gsacList = new ArrayList<BT3Cutscene>();
		bgmList = new ArrayList<Short>(); 
		addrList = new ArrayList<Short>();
		bgmNames = new ArrayList<String>();
		int pos=0, curr=0, gsacID=0; short data, bgmCnt=0;
		if (isFaultyGSC()) return;
		//traverse file until "01 05 0A 00" is found (responsible for starting battle params)
		while (curr != 0x01050A00)
		{
			curr = gsc.readInt(); 
			pos++; gsc.seek(pos);
		}
		pos+=8; gsc.seek(pos); //skip the string itself AND the 1st offset
		data = gsc.readShort(); //read 2nd offset
		addrList.add((short) (4*LittleEndian.getShort(data))); //multiply it by 4, then add it to the address list
		//go through all GSACs until GSDT is found
		while (curr != 0x47534454)
		{
			curr = gsc.readInt();
			int currAsLittleEndian = LittleEndian.getInt(curr);
			//check for GSAC header
			if (currAsLittleEndian>=10000 && currAsLittleEndian<=10050) gsacID = currAsLittleEndian;
			//reset counter after skipping GSAC header
			if (curr == 0x01000300) bgmCnt=0;
			//check if there is a song change in the current GSAC	
			if (curr == 0x0101DD05) 
			{
				pos+=5; gsc.seek(pos); //skip the string itself
				data = gsc.readShort(); //read corresponding offset
				addrList.add((short) (4*LittleEndian.getShort(data))); //multiply by 4, then add to address list
				bgmCnt++;
			}
			//add GSAC to list if end of said GSAC has been reached
			if (curr == 0x01000400) gsacList.add(new BT3Cutscene(gsacID, bgmCnt));
			pos++; gsc.seek(pos);
		}
		//keep track of GSDT header, then skip it
		pos+=15; gsc.seek(pos);
		int prev=-1, gsdtStart = pos;
		short addr; bgmCnt=0;
		//traverse GSDT until end of file (EOFC) is reached
		while ((curr != 0x454F4643))
		{
			curr = gsc.readInt();
			data = (short) LittleEndian.getInt(curr);
			addr = addrList.get(bgmCnt);
			//check if relative position (in GSDT) matches address from list
			if (pos-gsdtStart==addr)
			{
				bgmList.add(data); bgmCnt++; 
				pos=gsdtStart; //performance sacrifice
			}
			//break out of loop if no more addresses are found
			if (bgmCnt == addrList.size()) break;
			//compare current and previous addresses (since the list isn't sorted)
			if (addr<prev)
			{
				pos=gsdtStart; //reset position for next iteration
				prev=addr; //update previous address to prevent redundant comparisons
				continue;
			}
			pos+=4; gsc.seek(pos);
			prev=addr; //update previous address for next comparison
		}
		pos=0; prev=0; Scanner sc=null;
		int bgmIndex=0, bgmID=0; String currLine, bgmName=null;
		bgmCSV.seek(0);
		while (bgmCSV.getFilePointer() != bgmCSV.length())
		{
			//read whole line, then split it and assign it to 2 variables (ID, name)
			currLine = bgmCSV.readLine();
			sc = new Scanner(currLine);
			while (sc.hasNextLine()) 
			{
				String input = sc.nextLine();
				String[] inputArr = input.split(",");
				bgmID=Integer.parseInt(inputArr[0]); bgmName=inputArr[1];
			}
			//check if currently found ID matches any ID from BGM list
			if (bgmID == bgmList.get(bgmIndex))
			{
				bgmNames.add(bgmName); bgmIndex++;
				if (bgmIndex >= bgmList.size()) break;	
				prev = bgmID;
				bgmCSV.seek(0); //performance sacrifice
			}	
			if (prev>bgmList.get(bgmIndex))  
			{
				bgmCSV.seek(0); prev=0; //reset position and temp variables
			}
		}
		sc.close();
		printSongChanges();
	}
	public static void printSongChanges()
	{
		System.out.println(String.format("%-50s [ Initial BGM  ]", bgmNames.get(0))); //print initial song
		int bgmIndex=0;
		for (BT3Cutscene gsac: gsacList)
		{
			int bgmCnt = gsac.getBgmCnt();
			//break out of loop if no more songs are found
			if (bgmIndex == bgmList.size()) return;
			if (bgmCnt>0) //only print out songs from GSACs that feature BGM changes
			{
				for (int i=0; i<bgmCnt; i++) //blame Team BT4 for this for-loop's existance
				{
					bgmIndex++;
					System.out.println(String.format("%-50s [Cutscene %d]", bgmNames.get(bgmIndex), gsac.getGsacID()));
				}
			}
		}
	}
	public static void main(String[] args) throws IOException 
	{
		bgmCSV = new RandomAccessFile("./csv/songs.csv", "r");
		File gscFolder = new File("./gsc/");
		File[] gscPaths = gscFolder.listFiles((dir, name) ->
		(
			name.startsWith("GSC-B-") && (name.toLowerCase().endsWith(".gsc") || name.toLowerCase().endsWith(".unk"))
		));		
		int gscCnt = gscPaths.length;
		if (gscCnt!=0)
		{
			RandomAccessFile[] gscFiles = new RandomAccessFile[gscCnt];
			for (int i=0; i<gscCnt; i++)
				gscFiles[i] = new RandomAccessFile(gscPaths[i].getAbsolutePath(),"r");
			for (int i=0; i<gscCnt; i++)
			{
				String fileName = gscPaths[i].getName(); 
				int fileNameLen = fileName.length(), pos=(54-fileNameLen)/2;
				for (int j=0; j<42; j++)
				{
					if (j<pos) System.out.print('=');
					else if (j==pos) System.out.print(" Results for "+fileName+' ');
					else System.out.print('=');
				}
				System.out.println();
				gsc = gscFiles[i];
				long start = System.currentTimeMillis();
				detectSongsInGSC();
				long finish = System.currentTimeMillis();
				double time = (finish-start)/(double)1000;
				totalTime+=time;
				System.out.println("Time: "+time+" s");
			}
			gsc.close(); bgmCSV.close();
			System.out.println("\nTotal Time: "+totalTime+" s");
		}
	}
}