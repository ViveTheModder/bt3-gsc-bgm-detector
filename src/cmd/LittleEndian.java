package cmd;
//Little Endian class by ViveTheModder
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LittleEndian 
{
	public static short getShort(short data)
	{
		ByteBuffer buffer = ByteBuffer.allocate(2);
		buffer.asShortBuffer().put(data);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		return buffer.getShort();
	}
	public static int getInt(int data)
	{
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.asIntBuffer().put(data);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		return buffer.getInt();
	}
}