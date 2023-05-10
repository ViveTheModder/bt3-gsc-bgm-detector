package cmd;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Scanner;

public class MainApp 
{
	/* method that returns a short in little endian */
	public static short getLittleEndianShort(short data)
	{
		ByteBuffer buffer = ByteBuffer.allocate(2);
		buffer.asShortBuffer().put(data);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		return buffer.getShort();
	}
	
	/* method that returns an integer in little endian */
	public static int getLittleEndianInt(int data)
	{
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.asIntBuffer().put(data);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		return buffer.getInt();
	}
	
	/* method that checks if the GSC file is an actual BT3 scenario file */
	public static boolean isInvalidGSC(RandomAccessFile gsc) throws IOException
	{
		int header = gsc.readInt(); gsc.seek(8); 
		short size = getLittleEndianShort(gsc.readShort());
		
		//check if header is different from "47 53 43 46" = GSCF (Game Scenario Contents of File)
		if (header != 0x47534346)
			return true;
		//compare the GSC file size with the file size indicated by the GSCF header
		if (size+32 != gsc.length())
			return true;
		
		return false;
	}
	
	/* method that prints out the name of each song and during which cutscene it plays */
	public static void printSongChanges(ArrayList<String> bgmNames, ArrayList<Short> bgmList, ArrayList<BT3Cutscene> gsacList)
	{
		System.out.println(String.format("%-50s [ Initial BGM  ]", bgmNames.get(0))); //print initial song
		int bgmIndex=0;
		for (BT3Cutscene gsac: gsacList)
		{
			int bgmCnt = gsac.getBgmCnt();
			if (bgmIndex == bgmList.size()) //break out of loop if no more songs are found
				return;
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
	/* main method */
	public static void main(String[] args) throws IOException 
	{
		RandomAccessFile gsc = new RandomAccessFile("GSC", "r");
		ArrayList<BT3Cutscene> gsacList = new ArrayList<BT3Cutscene>();
		ArrayList<Short> bgmList = new ArrayList<Short>();
		ArrayList<Short> addrList = new ArrayList<Short>();
		ArrayList<String> bgmNames = new ArrayList<String>();
		
		int pos=0, curr=0, gsacID = 0; short data, bgmCnt=0;
		
		//traverse file until "01 05 0A 00" is found (responsible for starting battle params)
		while (curr != 0x01050A00)
		{
			curr = gsc.readInt(); 
			pos++; gsc.seek(pos);
		}
		
		pos+=8; gsc.seek(pos); //skip the string itself AND the 1st pointer
		data = gsc.readShort(); //read 2nd pointer
		addrList.add((short) (4*getLittleEndianShort(data))); //multiply it by 4, then add it to the address list

		//go through all GSACs until GSDT is found
		while (curr != 0x47534454)
		{
			curr = gsc.readInt();
			int currAsLittleEndian = getLittleEndianInt(curr);
			
			if (currAsLittleEndian>=10000 && currAsLittleEndian<=10050) //check for GSAC header
				gsacID = currAsLittleEndian;
			
			if (curr == 0x01000300) //reset counter after skipping GSAC header
				bgmCnt=0;
				
			if (curr == 0x0101DD05) //check if there is a song change in the current GSAC
			{
				pos+=5; gsc.seek(pos); //skip the string itself
				data = gsc.readShort(); //read corresponding pointer
				addrList.add((short) (4*getLittleEndianShort(data))); //multiply by 4, then add to address list
				bgmCnt++;
			}
			
			if (curr == 0x01000400) //add GSAC to list if end of said GSAC has been reached
				gsacList.add(new BT3Cutscene(gsacID, bgmCnt));
				
			pos++; gsc.seek(pos);
		}
		
		//keep track of GSDT header, then skip it
		pos+=15; gsc.seek(pos);
		int prev=-1, gsdtStart = pos;
		short addr; //current address in list
		bgmCnt=0;
		
		//traverse GSDT until end of file (EOFC) is reached
		while (curr != 0x454F4643)
		{
			data = gsc.readShort();
			curr = gsc.readInt();
			addr = addrList.get(bgmCnt);

			//check if relative position (in GSDT) matches address from list
			if (pos-gsdtStart == addrList.get(bgmCnt))
			{
				bgmList.add(getLittleEndianShort(data));
				bgmCnt++; //if so, add to list, then increment counter
			}
			//break out of loop if no more addresses are found
			if (bgmCnt == addrList.size())
				break;
			//compare current and previous addresses (since the list isn't sorted)
			if (addr<prev)
			{
				pos=gsdtStart; //reset position for next iteration
				prev=addr; //update previous address to prevent redundant comparisons
				continue; //skip current iteration
			}	
			pos+=4; gsc.seek(pos);
			prev=addr; //update previous address for next comparison
		}
		gsc.close();
		
		pos=0; prev=0; Scanner sc = null;
		int bgmIndex=0, bgmID=0; String currLine, bgmName=null;	
		
		RandomAccessFile bgmCSV = new RandomAccessFile("songs.csv", "r");
		while (bgmCSV.getFilePointer() != bgmCSV.length())
		{
			//read whole line, then split it and assign it to 2 variables (ID, name)
			currLine = bgmCSV.readLine();
			sc = new Scanner(currLine);
			sc.useDelimiter(";");
			while (sc.hasNext()) 
			{
				bgmID = sc.nextInt();
				bgmName = sc.nextLine();
				bgmName = bgmName.replace(";", ""); //remove unnecessary semi-colon
			}
			
			if (bgmID == bgmList.get(bgmIndex)) //check if currently found ID matches any ID from BGM list
			{
				bgmNames.add(bgmName); //add to BGM names list
				bgmIndex++; //increment then check if index exceeds BGM list size
				if (bgmIndex >= bgmList.size())
					break;	
				prev = bgmID; //assign previous BGM ID to temp variable
			}	
			//reset position and temp variable to 0 if next BGM ID is smaller than previous
			if (prev>bgmList.get(bgmIndex))  
			{
				bgmCSV.seek(0);
				prev=0;
			}
		}
		sc.close(); bgmCSV.close();
		printSongChanges(bgmNames, bgmList, gsacList);
	}
}