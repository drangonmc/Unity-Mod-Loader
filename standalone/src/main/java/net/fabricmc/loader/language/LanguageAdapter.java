package net.fabricmc.loader.language;
public interface LanguageAdapter {
    Object createInstance(Class<?> type,Options options) throws LanguageAdapterException;
    default Object createInstance(String name,Options options)throws ClassNotFoundException,LanguageAdapterException{
        try{return createInstance(Class.forName(name,true,Thread.currentThread().getContextClassLoader()),options);}
        catch(NoClassDefFoundError missing){if(options.getMissingSuperclassBehavior()==MissingSuperclassBehavior.RETURN_NULL)return null;throw missing;}
    }
    enum MissingSuperclassBehavior { RETURN_NULL,CRASH }
    class Options {
        private MissingSuperclassBehavior behavior=MissingSuperclassBehavior.CRASH;
        public MissingSuperclassBehavior getMissingSuperclassBehavior(){return behavior;}
        public static class Builder{
            private final Options options=new Options();
            public static Builder create(){return new Builder();}
            public Builder missingSuperclassBehaviour(MissingSuperclassBehavior value){options.behavior=value;return this;}
            public Options build(){return options;}
        }
    }
}
