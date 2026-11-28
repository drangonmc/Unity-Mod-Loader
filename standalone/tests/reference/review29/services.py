import re,time
from gameplay import eventually

def homes(server,client,first,paper=False):
    command="minecraft:tp" if paper else "tp"
    name="VolumeA"
    if first:
        for node in ("essentials.sethome","essentials.home","essentials.teleport.timer.bypass","essentials.teleport.cooldown.bypass"):
            server.send("lp user "+name+" permission set "+node+" true")
        time.sleep(1.0)
        server.send(command+" VolumeA 24.5 80 5.5 0 0","Teleported")
        eventually(lambda:client.act("qaPlayer"),lambda p:abs(p["x"]-24.5)<0.35)
        client.act("qaCommand",cmd="sethome")
        # Console commands only create the independent position for the check.
        time.sleep(.75)
    server.send(command+" VolumeA 29.5 80 5.5 0 0","Teleported")
    eventually(lambda:client.act("qaPlayer"),lambda p:abs(p["x"]-29.5)<.35)
    client.act("qaCommand",cmd="home")
    actual=eventually(lambda:client.act("qaPlayer"),lambda p:abs(p["x"]-24.5)<.35 and abs(p["z"]-5.5)<.35,18)
    position=server.send("data get entity VolumeA Pos","entity data:")
    coords=re.search(r"entity data: \[([^\]]+)",position)[1]
    xyz=[float(v.strip().rstrip("d")) for v in coords.split(",")]
    assert abs(xyz[0]-actual["x"])<.35 and abs(xyz[2]-actual["z"])<.35
    return {"xyz":xyz,"client":[actual[k] for k in ("x","y","z")]}
