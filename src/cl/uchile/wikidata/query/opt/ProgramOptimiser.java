package cl.uchile.wikidata.query.opt;

import cl.uchile.wikidata.query.datalog.Program;

public interface ProgramOptimiser {
	public Program optimise(Program p);
}
