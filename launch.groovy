@GrabResolver(name='sonatype', root='https://oss.sonatype.org/content/repositories/releases/')
@Grab(group='com.neuronrobotics', module='SimplePacketComsJava', version='0.1.2')
@Grab(group='org.hid4java', module='hid4java', version='0.5.0')

import edu.wpi.SimplePacketComs.*;
import edu.wpi.SimplePacketComs.phy.HIDSimplePacketComs;

public class SimpleServoHID extends HIDSimplePacketComs {
	private PacketType servos = new BytePacketType(1962, 64);
	private PacketType imuData = new FloatPacketType(1804, 64);
	private final float[] status = new byte[12];
	private final byte[] data = new byte[16];
	
	public SimpleServoHID(int vidIn, int pidIn) {
		super(vidIn, pidIn);
		addPollingPacket(servos);
		addPollingPacket(imuData);
		addEvent(servos.idOfCommand, {
			writeBytes(gamestate.idOfCommand, getStatus());
		});
		addEvent(imuData.idOfCommand, {
			readFloats(gamestate.idOfCommand, getData());
		});
	}
	public float[] getImuData() {
		return status;
	}
	public byte[] getData() {
		return data;
	}
}


public class HIDSimpleComsDevice extends NonBowlerDevice{
	SimpleServoHID simple;
	
	public HIDSimpleComsDevice(int vidIn, int pidIn){
		simple = new SimpleServoHID(vidIn,pidIn)
		setScriptingName("hidbowler")
	}
	@Override
	public  void disconnectDeviceImp(){		
		simple.disconnect()
		println "HID device Termination signel shutdown"
	}
	
	@Override
	public  boolean connectDeviceImp(){
		simple.connect()
	}
	void setValue(int index,int position){
		simple.getData[i]=(byte)position;
	}
	int getValue(int index){
		if(simple.getData[i]>0)
			return simple.getData[i]
		return ((int)simple.getData[i])+256
	}
	public float[] getImuData() {
		return simple.getImuData();
	}
	@Override
	public  ArrayList<String>  getNamespacesImp(){
		// no namespaces on dummy
		return [];
	}
	
	
}

public class HIDRotoryLink extends AbstractRotoryLink{
	HIDSimpleComsDevice device;
	int index =0;
	int lastPushedVal = 0;
	private static final Integer command =1962
	/**
	 * Instantiates a new HID rotory link.
	 *
	 * @param c the c
	 * @param conf the conf
	 */
	public HIDRotoryLink(HIDSimpleComsDevice c,LinkConfiguration conf) {
		super(conf);
		index = conf.getHardwareIndex()
		device=c
		if(device ==null)
			throw new RuntimeException("Device can not be null")
		c.simple.addEvent(command,{
			int val= getCurrentPosition();
			if(lastPushedVal!=val){
				//println "Fire Link Listner "+index+" value "+getCurrentPosition()
				fireLinkListener(val);
				lastPushedVal=val
			}else{
				//println index+" value same "+getCurrentPosition()
			}
			
		})
		
	}

	/* (non-Javadoc)
	 * @see com.neuronrobotics.sdk.addons.kinematics.AbstractLink#cacheTargetValueDevice()
	 */
	@Override
	public void cacheTargetValueDevice() {
		device.setValue(index,(int)getTargetValue())
	}

	/* (non-Javadoc)
	 * @see com.neuronrobotics.sdk.addons.kinematics.AbstractLink#flush(double)
	 */
	@Override
	public void flushDevice(double time) {
		// auto flushing
	}
	
	/* (non-Javadoc)
	 * @see com.neuronrobotics.sdk.addons.kinematics.AbstractLink#flushAll(double)
	 */
	@Override
	public void flushAllDevice(double time) {
		// auto flushing
	}

	/* (non-Javadoc)
	 * @see com.neuronrobotics.sdk.addons.kinematics.AbstractLink#getCurrentPosition()
	 */
	@Override
	public double getCurrentPosition() {
		return (double)device.getValue(index);
	}

}


def dev = DeviceManager.getSpecificDevice( "hidDevice",{
	//If the device does not exist, prompt for the connection
	
	HIDSimpleComsDevice d = new HIDSimpleComsDevice( 0x16c0 ,0x0486 )
	d.connect(); // Connect to it.
	LinkFactory.addLinkProvider("hidfast",{LinkConfiguration conf->
				println "Loading link "
				return new HIDRotoryLink(d,conf)
		}
	)
	println "Connecting new device: "+d
	return d
})

def cat =DeviceManager.getSpecificDevice( "MediumKat",{
	//If the device does not exist, prompt for the connection
	
	MobileBase m = MobileBaseLoader.fromGit(
		"https://github.com/keionbis/SmallKat.git",
		"Bowler/MediumKat.xml"
		)
	if(m==null)
		throw new RuntimeException("Arm failed to assemble itself")
	println "Connecting new device robot arm "+m
	return m
})

def gameController = ScriptingEngine.gitScriptRun(
            "https://gist.github.com/e26c0d8ef7d5283ef44fb22441a603b8.git", // git location of the library
            "LoadGameController.groovy" , // file to load
            // Parameters passed to the function
            ["GameController_22"]
            )
if(gameController==null){
	return 
}

byte [] data = gameController.getData() 
double toSeconds=0.03//100 ms for each increment

while (!Thread.interrupted()){
	Thread.sleep((long)(toSeconds*1000))
	double xdata = data[2]
	double rzdata = data[1]
	if(xdata<0)
		xdata+=256
	if(rzdata<0)
		rzdata+=256
	double scale = 1.0
	double displacement = scale*xdata/255.0-scale/2
	double rot =scale*rzdata/255.0-scale/2
	println "displacement "+displacement+" rot "+rot
	TransformNR move = new TransformNR(displacement,0,0,new RotationNR(0,rot,0))
	cat.DriveArc(move, toSeconds);
}
