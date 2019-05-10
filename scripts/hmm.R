# R test implementation for discovery components of XHMM algorithm
# Implements steps in http://atgu.mgh.harvard.edu/xhmm/tutorial.shtml (using demo data)

library(Rmpfr)

XHMM_RUN_DIR <- "../deca-core/src/test/resources"

D <- 70000
p <- 1e-8
q <- 1/6
M <- 3

read.xhmm <- function(filename) {
  # Convenience function to read XHMM table outputs
  d <- as.matrix(read.table(filename, header=T, row.names=1))
  colnames(d) <- as.character(read.table(filename, header=F, row.names=1, nrow=1, stringsAsFactors=F))
  d
}

targets2data.frame <- function(targets) {
  # Convert string regions, chr:beg-end to data.frame with chr, beg and end columns
  library(stringr)
  
  split <- unlist(strsplit(targets,"[:-]"))
  data.frame(
    chr=split[seq.int(1L, length(split), 3L)],
    beg=as.integer(split[seq.int(2L, length(split), 3L)]),
    end=as.integer(split[seq.int(3L, length(split), 3L)])
  )
}

trans.matrix <- function(f) {
  mpfrArray(c(
    f*(1-q)+(1-f)*p, p, (1-f)*p, f*q + (1-f)*(1-2*p), 1-2*p, f*q+(1-f)*(1-2*p), (1-f)*p, p, f*(1-q)+(1-f)*p    
  ), prec=80, dim=c(3,3))
}

phred <- function(p, max=99) {
  min(-10*log10(1-p), max)
}

Z <- read.xhmm(file.path(XHMM_RUN_DIR,"DATA.PCA_normalized.filtered.sample_zscores.RD.txt"))

d <- rep(0, ncol(Z))
d[2:length(d)] <- {
  # Compute the distance between adjacent targets
  targets <- targets2data.frame(colnames(Z))
  (targets$beg[-1] - targets$end[1:length(targets$end)-1]) - 1
}

hmm <- function(i) {  
  emit <- mpfrArray(NA,prec=80,dim = c(3,ncol(Z)))
  emit[1,] <- dnorm(Z[i,], mean=-M, sd=1)
  emit[2,] <- dnorm(Z[i,], mean=0, sd=1)
  emit[3,] <- dnorm(Z[i,], mean=M, sd=1)

  # Determine path with Viterbi algorithm
  vit <- mpfrArray(NA, prec = 80, dim = c(3,ncol(Z)))
  back <- matrix(nrow=3, ncol=ncol(Z))
  vit[,1] <- c(p, 1- 2*p, p) * emit[,1]
  for (j in 2:ncol(Z)) {
    trans <- trans.matrix(exp(-d[j]/D))
    for (s in 1:3) {
      incoming <- vit[,j-1] * trans[,s] * emit[s,j]
      back[s,j] <- which.max(incoming)
      vit[s,j] <- incoming[back[s,j]]
    }
  }
  path <- rep(which.max(vit[,ncol(Z)]), ncol(Z))
  for (j in (ncol(Z)-1):1) {
    path[j] <- back[path[j+1],j+1]
  }

  # Forward-backward
  fwd <- mpfrArray(NA, prec = 80, dim = c(3,ncol(Z)))
  bwd <- mpfrArray(NA, prec = 80, dim = c(3,ncol(Z)))
  
  fwd[,1] <- c(p, 1- 2*p, p) * emit[,1]
  for (j in 2:ncol(Z)) {
    trans <- trans.matrix(exp(-d[j]/D))
    for (s in 1:3) {
      # Forward algorithm without normalization
      fwd[s,j] <- sum(fwd[,j-1] * trans[,s] * emit[s,j])
    }
  }
  
  bwd[,ncol(Z)] <- 1
  for (j in (ncol(Z)-1):1) {
    trans <- trans.matrix(exp(-d[j+1]/D))
    for (s in 1:3) {
      # Backward algorithm
      edges <- bwd[,j+1] * emit[,j+1] * trans[s,] 
      bwd[s,j] <- sum(edges)
    }
  }

  list(path=path, fwd=fwd, bwd=bwd, emit=emit)  
}

normHmm <- function(i) {
  emit <- mpfrArray(NA,prec=80,dim = c(3,ncol(Z)))
  emit[1,] <- dnorm(Z[i,], mean=-M, sd=1)
  emit[2,] <- dnorm(Z[i,], mean=0, sd=1)
  emit[3,] <- dnorm(Z[i,], mean=M, sd=1)

  fwd <- mpfrArray(NA, prec = 80, dim = c(3,ncol(Z)))
  bwd <- mpfrArray(NA, prec = 80, dim = c(3,ncol(Z)))
  C <- mpfrArray(NA, prec = 80, dim = c(1,ncol(Z)))

  fwd[,1] <- c(p, 1- 2*p, p) * emit[,1]
  C[1] <- sum(fwd[,1])
  fwd[,1] <- fwd[,1] / C[1]
  for (j in 2:ncol(Z)) {
    trans <- trans.matrix(exp(-d[j]/D))
    for (s in 1:3) {
      # Forward algorithm with normalization
      fwd[s,j] <- sum(fwd[,j-1] * trans[,s] * emit[s,j])
    }
    C[j] <- sum(fwd[,j])
    fwd[,j] = fwd[,j] / C[j]
  }

  bwd[,ncol(Z)] <- 1/C[ncol(Z)]
  for (j in (ncol(Z)-1):1) {
    trans <- trans.matrix(exp(-d[j+1]/D))
    for (s in 1:3) {
      # Backward algorithm with normalization
      edges <- bwd[,j+1] * emit[,j+1] * trans[s,]
      bwd[s,j] <- sum(edges) / C[j]
    }
  }

  list(fwd=fwd, bwd=bwd, C=C, emit=emit)
}


scores <- function(fwd, bwd, emit, start, end, state) {
  exact <- fwd[state,start]
  for (j in (start+1):end) {
    trans <- trans.matrix(exp(-d[j]/D))
    exact <- exact * trans[state,state] * emit[state,j]
  }
  exact <- exact * bwd[state,end] / sum(fwd[,ncol(fwd)])

  diploid <- fwd[2,start]
  for (j in (start+1):end) {
    trans <- trans.matrix(exp(-d[j]/D))
    diploid <- diploid * trans[2,2] * emit[2,j]
  }
  diploid <- diploid * bwd[2,end] / sum(fwd[,ncol(fwd)]) 

  local_fwd <- mpfrArray(0.0, prec = 80, dim = c(3,(end-start+1)))
  local_fwd[state:2,1] <- fwd[state:2,start]
  for (j in (start+1):end) {
    trans <- trans.matrix(exp(-d[j]/D))
    for (s in state:2) {
      local_fwd[s,j-(start-1)] <- sum(local_fwd[,j-start] * trans[,s] * emit[s,j])
    } 
  }
  some <- (sum(local_fwd[,ncol(local_fwd)] * bwd[,end])/ sum(fwd[,ncol(fwd)])) - diploid   

  c(exact=phred(exact), some=phred(some)) 
}

normScores <- function(fwd, bwd, emit, C, start, end, state) {
  exact <- fwd[state,start]
  for (j in (start+1):end) {
    trans <- trans.matrix(exp(-d[j]/D))
    exact <- exact * trans[state,state] * emit[state,j]
  }
  exact <- (exact * bwd[state,end]) / prod(C[(start+1):(end-1)])

  diploid <- fwd[2,start]
  for (j in (start+1):end) {
    trans <- trans.matrix(exp(-d[j]/D))
    diploid <- diploid * trans[2,2] * emit[2,j]
  }
  diploid <- (diploid * bwd[2,end]) / prod(C[(start+1):(end-1)])

  local_fwd <- mpfrArray(0.0, prec = 80, dim = c(3,(end-start+1)))
  local_fwd[state:2,1] <- fwd[state:2,start]
  local_C <- mpfrArray(1.0, prec = 80, dim = c(1,(end-start+1)))
  for (j in (start+1):end) {
    trans <- trans.matrix(exp(-d[j]/D))
    for (s in state:2) {
      local_fwd[s,j-(start-1)] <- sum(local_fwd[,j-start] * trans[,s] * emit[s,j])
    }
    local_C[j-(start-1)] <- sum(local_fwd[,j-(start-1)])
    local_fwd[,j-(start-1)] = local_fwd[,j-(start-1)] / local_C[j-(start-1)]
  }
  some <- (sum(local_fwd[,ncol(local_fwd)] * bwd[,end]) * prod(local_C) / prod(C[(start+1):(end-1)])) - diploid

  c(exact=phred(exact), some=phred(some))
}

# Test on the deletion in HG00121 (row 15) from targets 104 to 117
model <- hmm(15)
normModel <- normHmm(15)
scores(model$fwd, model$bwd, model$emit, 104, 117, 1)
normScores(normModel$fwd, normModel$bwd, normModel$emit, normModel$C, 104, 117, 1)

# Test on the duplication in HG00113 (row 19) from targets 4 to 11
model <- hmm(19)
normModel <- normHmm(19)
scores(model$fwd, model$bwd, model$emit, 4, 11, 3)
normScores(normModel$fwd, normModel$bwd, normModel$emit, normModel$C, 4, 11, 3)
  