package cmd;

public class BT3Cutscene 
{
	int gsacID;
	short bgmCnt;
	public BT3Cutscene(int gsacID, short bgmCnt)
	{
		this.gsacID = gsacID;
		this.bgmCnt = bgmCnt;
	}
	public int getGsacID() 
	{
		return gsacID;
	}

	public short getBgmCnt() 
	{
		return bgmCnt;
	}
}
